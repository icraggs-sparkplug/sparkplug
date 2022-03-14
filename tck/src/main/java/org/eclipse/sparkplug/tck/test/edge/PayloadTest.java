
/**
 * Copyright (c) 2022 Anja Helmbrecht-Schaar
 * <p>
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * <p>
 * SPDX-License-Identifier: EPL-2.0
 * <p>
 * Contributors:
 * Anja Helmbrecht-Schaar - initial implementation and documentation
 */
package org.eclipse.sparkplug.tck.test.edge;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hivemq.extension.sdk.api.annotations.NotNull;
import com.hivemq.extension.sdk.api.packets.connect.ConnectPacket;
import com.hivemq.extension.sdk.api.packets.disconnect.DisconnectPacket;
import com.hivemq.extension.sdk.api.packets.publish.PublishPacket;
import com.hivemq.extension.sdk.api.packets.subscribe.SubscribePacket;
import org.eclipse.sparkplug.tck.sparkplug.Sections;
import org.eclipse.sparkplug.tck.test.TCK;
import org.eclipse.sparkplug.tck.test.TCKTest;
import org.eclipse.sparkplug.tck.test.common.Utils;
import org.eclipse.tahu.message.model.Metric;
import org.eclipse.tahu.message.model.SparkplugBPayload;
import org.eclipse.tahu.protobuf.SparkplugBProto;
import org.jboss.test.audit.annotations.SpecAssertion;
import org.jboss.test.audit.annotations.SpecVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.eclipse.sparkplug.tck.test.common.Requirements.*;
import static org.eclipse.sparkplug.tck.test.common.TopicConstants.*;
import static org.eclipse.sparkplug.tck.test.common.Utils.extractSparkplugPayload;
import static org.eclipse.sparkplug.tck.test.common.Utils.setResult;

/**
 * This is the edge node Sparkplug payload validation.
 *
 * @author Anja Helmbrecht-Schaar
 */
@SpecVersion(
        spec = "sparkplug",
        version = "3.0.0-SNAPSHOT")
public class PayloadTest extends TCKTest {

    private static final @NotNull Logger logger = LoggerFactory.getLogger("Sparkplug");
    public static final String PROPERTY_KEY_QUALITY = "Quality";

    private final @NotNull Map<String, String> testResults = new HashMap<>();
    private final @NotNull ArrayList<String> testIds = new ArrayList<>();
    private final @NotNull TCK theTCK;

    private @NotNull String deviceId;
    private @NotNull String groupId;
    private @NotNull String edgeNodeId;
    private @NotNull String hostApplicationId;
    private @NotNull long seqUnassigned = -1;

    public PayloadTest(final @NotNull TCK aTCK, final @NotNull String[] parms) {
        logger.info("Edge Node payload validation test. Parameters: {} ", Arrays.asList(parms));
        theTCK = aTCK;

        if (parms.length < 4) {
            logger.error("Parameters to edge payload test must be: {hostId}, groupId edgeNodeId deviceId");
            return;
        }

        hostApplicationId = parms[0];
        groupId = parms[1];
        edgeNodeId = parms[2];
        deviceId = parms[3];
        logger.info("Parameters are HostId: {}, GroupId: {}, EdgeNodeId: {}, DeviceId: {}", hostApplicationId, groupId, edgeNodeId, deviceId);
    }

    public void endTest() {

        Utils.setEndTest(getName(), testIds, testResults);
        reportResults(testResults);
    }

    public String getName() {
        return "PayloadTest";
    }

    public String[] getTestIds() {
        return testIds.toArray(new String[0]);
    }

    public Map<String, String> getResults() {
        return testResults;
    }

    @Override
    public void connect(final @NotNull String clientId, final @NotNull ConnectPacket packet) {
        // TODO Auto-generated method stub
    }

    @Override
    public void disconnect(String clientId, DisconnectPacket packet) {
        // TODO Auto-generated method stub

    }

    @Override
    public void subscribe(final @NotNull String clientId, final @NotNull SubscribePacket packet) {
        // TODO Auto-generated method stub
    }

    @Override
    public void publish(final @NotNull String clientId, final @NotNull PublishPacket packet) {
        final String topic = packet.getTopic();
        logger.info("Edge - Payload validation test - publish - topic: {}", topic);

        boolean isSparkplugTopic = topic.startsWith(TOPIC_ROOT_SP_BV_1_0);
        boolean isDataTopic = isSparkplugTopic
                && (topic.contains(TOPIC_PATH_DDATA) || topic.contains(TOPIC_PATH_NDATA));
        boolean isCommandTopic = isSparkplugTopic
                && (topic.contains(TOPIC_PATH_NCMD) || topic.contains(TOPIC_PATH_DCMD));

        if (!isSparkplugTopic) {
            logger.error("Skip Edge payload validation - no sparkplug payload.");
            return;
        }

        try {
            checkDatatypeValidType(packet);
            checkPropertiesValidType(packet, topic);
            checkSequenceNumberIncluded(packet, topic);

            if (isDataTopic) {
                checkDataTopicPayload(clientId, packet, topic);
            } else if (isCommandTopic) {
                checkCommandTopicPayload(clientId, packet, topic);
            }
        } finally {
            theTCK.endTest();
        }
    }

    private void checkDataTopicPayload(final @NotNull String clientId, final @NotNull PublishPacket packet,
                                       final @NotNull String topic) {
        if (clientId.contentEquals(deviceId)
                || topic.contains(groupId) && topic.contains(edgeNodeId)) {
            final SparkplugBPayload sparkplugPayload = extractSparkplugPayload(packet);
            if (sparkplugPayload != null) {
                checkPayloadsNameRequirement(sparkplugPayload);
                checkAliasInData(sparkplugPayload, topic);
                checkMetricsDataTypeNotRec(sparkplugPayload, topic);
                checkPayloadsNameInDataRequirement(sparkplugPayload);
            } else {
                logger.error("Skip Edge payload validation - no sparkplug payload.");
            }
        }
    }

    private void checkCommandTopicPayload(final @NotNull String clientId, final @NotNull PublishPacket packet,
                                          final @NotNull String topic) {
        if (clientId.contentEquals(deviceId)
                || topic.contains(groupId) && topic.contains(edgeNodeId)) {
            final SparkplugBPayload sparkplugPayload = extractSparkplugPayload(packet);
            if (sparkplugPayload != null) {
                checkAliasInData(sparkplugPayload, topic);
                checkMetricsDataTypeNotRec(sparkplugPayload, topic);
                checkPayloadsNameRequirement(sparkplugPayload);
                checkPayloadsTimestampCommand(sparkplugPayload, topic);
            } else {
                logger.error("Skip Edge payload validation - no sparkplug payload.");
            }
        }
    }


    @SpecAssertion(
            section = Sections.PAYLOADS_B_PAYLOAD,
            id = ID_PAYLOADS_SEQUENCE_NUM_ALWAYS_INCLUDED)
    public void checkSequenceNumberIncluded(final @NotNull PublishPacket packet, String topic) {
        testIds.add(ID_PAYLOADS_SEQUENCE_NUM_ALWAYS_INCLUDED);
        logger.debug("Check Req: {} A sequence number MUST be included in the payload of every Sparkplug MQTT message except NDEATH messages.", ID_PAYLOADS_SEQUENCE_NUM_ALWAYS_INCLUDED);
        boolean isValid = false;
        SparkplugBProto.Payload result = null;
        try {
            result = Utils.parseRaw(packet);
        } catch (InvalidProtocolBufferException e) {
            isValid = false;
            logger.error("Check req set for : {}:  {}", ID_PAYLOADS_SEQUENCE_NUM_ALWAYS_INCLUDED, e.getMessage());
        }

        if (result != null) {
            if (result.getSeq() >= 0) {
                isValid = true;
            } else if (result.getSeq() == seqUnassigned && topic.contains(TOPIC_PATH_NDEATH)) {
                isValid = true;
            }
        }
        testResults.put(ID_PAYLOADS_SEQUENCE_NUM_ALWAYS_INCLUDED, setResult(isValid, PAYLOADS_SEQUENCE_NUM_ALWAYS_INCLUDED));
    }

    @SpecAssertion(
            section = Sections.PAYLOADS_B_METRIC,
            id = ID_PAYLOADS_METRIC_DATATYPE_VALUE_TYPE)
    @SpecAssertion(
            section = Sections.PAYLOADS_B_METRIC,
            id = ID_PAYLOADS_METRIC_DATATYPE_VALUE)
    public void checkDatatypeValidType(final @NotNull PublishPacket packet) {

        testIds.add(ID_PAYLOADS_METRIC_DATATYPE_VALUE_TYPE);
        testIds.add(ID_PAYLOADS_METRIC_DATATYPE_VALUE);

        boolean isValid_DataType = true;
        boolean isValid_DataTypeValue = true;
        SparkplugBProto.Payload result = null;

        logger.debug("Check Req: {} The datatype MUST be an unsigned 32-bit integer representing the datatype.", ID_PAYLOADS_METRIC_DATATYPE_VALUE_TYPE);
        try {
            result = Utils.parseRaw(packet);
        } catch (InvalidProtocolBufferException e) {
            isValid_DataType = false;
            isValid_DataTypeValue = false;
            logger.error("Check req set for : {}:  {}", ID_PAYLOADS_METRIC_DATATYPE_VALUE_TYPE, e.getMessage());
        }
        testResults.put(ID_PAYLOADS_METRIC_DATATYPE_VALUE_TYPE, setResult(isValid_DataType, PAYLOADS_METRIC_DATATYPE_VALUE_TYPE));

        logger.debug("Check Req: The datatype MUST be one of the enumerated values as shown in the valid Sparkplug Data Types.");
        if (result != null) {
            for (org.eclipse.tahu.protobuf.SparkplugBProto.Payload.Metric m : result.getMetricsList()) {
                if (SparkplugBProto.DataType.forNumber(m.getDatatype()) == null
                        || SparkplugBProto.DataType.Unknown == SparkplugBProto.DataType.forNumber(m.getDatatype())) {
                    isValid_DataTypeValue = false;
                    break;
                }
            }
        }

        testResults.put(ID_PAYLOADS_METRIC_DATATYPE_VALUE, setResult(isValid_DataTypeValue, PAYLOADS_METRIC_DATATYPE_VALUE));
    }

    @SpecAssertion(
            section = Sections.PAYLOADS_B_PROPERTYSET,
            id = ID_PAYLOADS_PROPERTYSET_KEYS_ARRAY_SIZE)
    @SpecAssertion(
            section = Sections.PAYLOADS_B_PROPERTYSET,
            id = ID_PAYLOADS_PROPERTYSET_VALUES_ARRAY_SIZE)
    @SpecAssertion(
            section = Sections.PAYLOADS_B_PROPERTYVALUE,
            id = ID_PAYLOADS_METRIC_PROPERTYVALUE_TYPE_TYPE)
    @SpecAssertion(
            section = Sections.PAYLOADS_B_PROPERTYVALUE,
            id = ID_PAYLOADS_METRIC_PROPERTYVALUE_TYPE_VALUE)
    @SpecAssertion(
            section = Sections.PAYLOADS_B_PROPERTYVALUE,
            id = ID_PAYLOADS_METRIC_PROPERTYVALUE_TYPE_REQ)
    @SpecAssertion(
            section = Sections.PAYLOADS_B_QUALITY_CODES,
            id = ID_PAYLOADS_PROPERTYSET_QUALITY_VALUE_TYPE)
    @SpecAssertion(
            section = Sections.PAYLOADS_B_QUALITY_CODES,
            id = ID_PAYLOADS_PROPERTYSET_QUALITY_VALUE_VALUE)
    public void checkPropertiesValidType(final @NotNull PublishPacket packet, String topic) {

        testIds.add(ID_PAYLOADS_PROPERTYSET_KEYS_ARRAY_SIZE);
        testIds.add(ID_PAYLOADS_PROPERTYSET_VALUES_ARRAY_SIZE);
        testIds.add(ID_PAYLOADS_METRIC_PROPERTYVALUE_TYPE_TYPE);
        testIds.add(ID_PAYLOADS_METRIC_PROPERTYVALUE_TYPE_VALUE);
        testIds.add(ID_PAYLOADS_METRIC_PROPERTYVALUE_TYPE_REQ);
        testIds.add(ID_PAYLOADS_PROPERTYSET_QUALITY_VALUE_TYPE);
        testIds.add(ID_PAYLOADS_PROPERTYSET_QUALITY_VALUE_VALUE);

        boolean isValid_KeyArraySize = true;
        boolean isValid_PropertyValueType = true;
        boolean isValid_PropertyValueTypeValue = true;
        boolean isValid_PropertyValueTypeReq = true;
        boolean qualityCodeSettingIsUsed = false;

        SparkplugBProto.Payload result = null;

        logger.debug("Check Req: {} The datatype MUST be an unsigned 32-bit integer representing the datatype.", ID_PAYLOADS_PROPERTYSET_KEYS_ARRAY_SIZE);
        try {
            result = Utils.parseRaw(packet);
        } catch (InvalidProtocolBufferException e) {
            isValid_KeyArraySize = false;
            isValid_PropertyValueType = false;
            isValid_PropertyValueTypeReq = false;
            logger.error("Check req set for : {}:  {}", Sections.PAYLOADS_B_PROPERTYVALUE, e.getMessage());
        }

        if (result != null) {

            logger.debug("Check Req: {} The array of keys in a PropertySet MUST contain the same number of values included in the array of PropertyValue objects.", PAYLOADS_PROPERTYSET_KEYS_ARRAY_SIZE);
            logger.debug("Check Req: {} The array of values in a PropertySet MUST contain the same number of items that are in the keys array.", PAYLOADS_PROPERTYSET_VALUES_ARRAY_SIZE);

            logger.debug("Check Req: {} This MUST be an unsigned 32-bit integer representing the datatype.", ID_PAYLOADS_METRIC_PROPERTYVALUE_TYPE_TYPE);
            logger.debug("Check Req: {} This value MUST be one of the enumerated values as shown in the Sparkplug Basic Data Types or the Sparkplug Property Value Data Types.", ID_PAYLOADS_METRIC_PROPERTYVALUE_TYPE_VALUE);
            logger.debug("Check Req: {} This MUST be included in Property Value Definitions in NBIRTH and DBIRTH messages.", ID_PAYLOADS_METRIC_PROPERTYVALUE_TYPE_REQ);

            for (org.eclipse.tahu.protobuf.SparkplugBProto.Payload.Metric m : result.getMetricsList()) {
                if (m.hasProperties()
                        && m.getProperties().getValuesList().size() != m.getProperties().getKeysList().size()) {
                    isValid_KeyArraySize = false;
                }
                //execute always, but set only if one is true
                qualityCodeSettingIsUsed = checkQualityCodeRequirement(m) || qualityCodeSettingIsUsed;

                for (int i = 0; i < m.getProperties().getValuesCount(); i++) {
                    final SparkplugBProto.Payload.PropertyValue propertyValue = m.getProperties().getValues(i);
                    if (SparkplugBProto.Payload.PropertyValue.ValueCase.forNumber(propertyValue.getType()) == null
                            || SparkplugBProto.Payload.PropertyValue.ValueCase.VALUE_NOT_SET == SparkplugBProto.Payload.PropertyValue.ValueCase.forNumber(propertyValue.getType())) {
                        isValid_PropertyValueType = false;
                        isValid_PropertyValueTypeValue = false;
                    }
                    if ((topic.contains(TOPIC_PATH_NBIRTH) || topic.contains(TOPIC_PATH_DBIRTH))
                            && SparkplugBProto.Payload.PropertyValue.ValueCase.forNumber(propertyValue.getType()) == SparkplugBProto.Payload.PropertyValue.ValueCase.VALUE_NOT_SET) {
                        isValid_PropertyValueTypeReq = false;
                    }
                }
            }
        }
        if (!qualityCodeSettingIsUsed) {
            //option was not used -so test is than passed by default - otherwise the result is set in the subroutine
            testResults.put(ID_PAYLOADS_PROPERTYSET_QUALITY_VALUE_TYPE, setResult(true, PAYLOADS_PROPERTYSET_QUALITY_VALUE_TYPE));
            testResults.put(ID_PAYLOADS_PROPERTYSET_QUALITY_VALUE_VALUE, setResult(true, PAYLOADS_PROPERTYSET_QUALITY_VALUE_VALUE));

        }
        testResults.put(ID_PAYLOADS_PROPERTYSET_KEYS_ARRAY_SIZE, setResult(isValid_KeyArraySize, PAYLOADS_PROPERTYSET_KEYS_ARRAY_SIZE));
        testResults.put(ID_PAYLOADS_PROPERTYSET_VALUES_ARRAY_SIZE, setResult(isValid_KeyArraySize, PAYLOADS_PROPERTYSET_VALUES_ARRAY_SIZE));

        testResults.put(ID_PAYLOADS_METRIC_PROPERTYVALUE_TYPE_TYPE, setResult(isValid_PropertyValueType, PAYLOADS_METRIC_PROPERTYVALUE_TYPE_TYPE));
        testResults.put(ID_PAYLOADS_METRIC_PROPERTYVALUE_TYPE_VALUE, setResult(isValid_PropertyValueTypeValue, PAYLOADS_METRIC_PROPERTYVALUE_TYPE_VALUE));
        testResults.put(ID_PAYLOADS_METRIC_PROPERTYVALUE_TYPE_REQ, setResult(isValid_PropertyValueTypeReq, PAYLOADS_METRIC_PROPERTYVALUE_TYPE_REQ));

    }


    private boolean checkQualityCodeRequirement(SparkplugBProto.Payload.Metric m) {
        //optional key - but if it is used - it must fit to requirements
        boolean qualityCodeSettingIsUsed = false;
        for (int i = 0; i < m.getProperties().getValuesCount(); i++) {
            final String key = m.getProperties().getKeys(i);
            if (key.equals(PROPERTY_KEY_QUALITY)) {
                final SparkplugBProto.Payload.PropertyValue propertyValue = m.getProperties().getValues(i);
                logger.debug("Check: Req: Property Value MUST be a value of 3 which represents a Signed 32-bit Integer.");
                if (!(propertyValue.getType() == SparkplugBProto.Payload.PropertyValue.ValueCase.LONG_VALUE.getNumber())) {
                    testResults.put(ID_PAYLOADS_PROPERTYSET_QUALITY_VALUE_TYPE, setResult(false, PAYLOADS_PROPERTYSET_QUALITY_VALUE_TYPE));
                }
                logger.debug("Check: Req: 'value' of the Property Value MUST be an int_value and be one of the valid quality codes of 0, 192, or 500.");
                if (!(propertyValue.getLongValue() == 0
                        || propertyValue.getLongValue() == 192 || propertyValue.getLongValue() == 500)) {
                    testResults.put(ID_PAYLOADS_PROPERTYSET_QUALITY_VALUE_VALUE, setResult(false, PAYLOADS_PROPERTYSET_QUALITY_VALUE_VALUE));
                }
                qualityCodeSettingIsUsed = true;
            }
        }
        return qualityCodeSettingIsUsed;
    }


    @SpecAssertion(
            section = Sections.PAYLOADS_B_METRIC,
            id = ID_PAYLOADS_ALIAS_DATA_CMD_REQUIREMENT)
    public void checkAliasInData(final @NotNull SparkplugBPayload sparkplugPayload, String topic) {
        testIds.add(ID_PAYLOADS_ALIAS_DATA_CMD_REQUIREMENT);
        logger.debug("Check Req: NDATA, DDATA, NCMD, and DCMD messages MUST only include an alias and the metric name MUST be excluded.");

        boolean isValid = false;
        if (topic.contains(TOPIC_PATH_NDATA) || topic.contains(TOPIC_PATH_DDATA)
                || topic.contains(TOPIC_PATH_NCMD) || topic.contains(TOPIC_PATH_DCMD)) {
            for (Metric m : sparkplugPayload.getMetrics()) {
                if (!m.getIsNull()
                        && (m.hasAlias() && m.getName().length() == 0))
                    isValid = true;
                break;
            }
            testResults.put(ID_PAYLOADS_ALIAS_DATA_CMD_REQUIREMENT, setResult(isValid, PAYLOADS_ALIAS_DATA_CMD_REQUIREMENT));
        }
    }

    @SpecAssertion(
            section = Sections.PAYLOADS_B_METRIC,
            id = ID_PAYLOADS_METRIC_DATATYPE_NOT_REQ)
    public void checkMetricsDataTypeNotRec(final @NotNull SparkplugBPayload sparkplugPayload, String topic) {
        testIds.add(ID_PAYLOADS_METRIC_DATATYPE_NOT_REQ);
        logger.debug("Check Req: The datatype SHOULD NOT be included with metric definitions in NDATA, NCMD, DDATA, and DCMD messages.");
        boolean isValid = true;
        if (topic.contains(TOPIC_PATH_NDATA) || topic.contains(TOPIC_PATH_DDATA)
                || topic.contains(TOPIC_PATH_NCMD) || topic.contains(TOPIC_PATH_DCMD)
        ) {
            for (Metric m : sparkplugPayload.getMetrics()) {
                if (m.getDataType() != null) {
                    isValid = false;
                    break;
                }
            }
            testResults.put(ID_PAYLOADS_METRIC_DATATYPE_NOT_REQ, setResult(isValid, PAYLOADS_METRIC_DATATYPE_NOT_REQ));
        }
    }


    @SpecAssertion(
            section = Sections.PAYLOADS_B_METRIC,
            id = ID_PAYLOADS_NAME_REQUIREMENT)
    public void checkPayloadsNameRequirement(final @NotNull SparkplugBPayload sparkplugPayload) {
        testIds.add(ID_PAYLOADS_NAME_REQUIREMENT);
        logger.debug("Check Req: The name MUST be included with every metric unless aliases are being used.");
        boolean isValid = true;
        for (Metric m : sparkplugPayload.getMetrics()) {
            if (m.getIsNull() || m.getName().isEmpty() && !m.hasAlias()) {
                isValid = false;
                break;
            }
        }
        testResults.put(ID_PAYLOADS_NAME_REQUIREMENT, setResult(isValid, PAYLOADS_NAME_REQUIREMENT));
    }


    @SpecAssertion(
            section = Sections.PAYLOADS_B_METRIC,
            id = ID_PAYLOADS_NAME_BIRTH_DATA_REQUIREMENT)
    public void checkPayloadsNameInDataRequirement(final @NotNull SparkplugBPayload sparkplugPayload) {
        testIds.add(ID_PAYLOADS_NAME_BIRTH_DATA_REQUIREMENT);
        logger.debug("Check Req: The timestamp MUST be included with every metric in all NBIRTH, DBIRTH, NDATA, and DDATA messages.");
        boolean isValid = true;
        for (Metric m : sparkplugPayload.getMetrics()) {
            if (m.getTimestamp() == null) {
                isValid = false;
                break;
            }
        }
        testResults.put(ID_PAYLOADS_NAME_BIRTH_DATA_REQUIREMENT, setResult(isValid, PAYLOADS_NAME_BIRTH_DATA_REQUIREMENT));
    }

    //TODO - To Be Discussed - what we should check for MAY
    @SpecAssertion(
            section = Sections.PAYLOADS_B_METRIC,
            id = ID_PAYLOADS_NAME_CMD_REQUIREMENT)
    public void checkPayloadsTimestampCommand(final @NotNull SparkplugBPayload sparkplugPayload, String topic) {
        testIds.add(ID_PAYLOADS_NAME_CMD_REQUIREMENT);
        logger.debug("Check Req: The timestamp MAY be included with metrics in NCMD and DCMD messages.");
        boolean isValid = false;
        if (topic.contains(TOPIC_PATH_NCMD) || topic.contains(TOPIC_PATH_DCMD)) {
            for (Metric m : sparkplugPayload.getMetrics()) {
                if (m.getTimestamp() == null) {
                    isValid = false;
                    break;
                }
            }
        }
        testResults.put(ID_PAYLOADS_NAME_CMD_REQUIREMENT, setResult(true, PAYLOADS_NAME_CMD_REQUIREMENT));
    }
}