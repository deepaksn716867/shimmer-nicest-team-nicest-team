/*
 * Copyright 2015 Open mHealth
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.openmhealth.shim.ihealth.mapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Lists;
import org.openmhealth.schema.domain.omh.*;
import org.openmhealth.shim.common.mapper.DataPointMapper;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.openmhealth.schema.domain.omh.DataPointModality.*;
import static org.openmhealth.shim.common.mapper.JsonNodeMappingSupport.*;


/**
 * @author Chris Schaefbauer
 */
public abstract class IHealthDataPointMapper<T> implements DataPointMapper<T, JsonNode> {

    public static final String RESOURCE_API_SOURCE_NAME = "iHealth Resource API";
    public static final String DATA_SOURCE_MANUAL = "Manual";
    public static final String DATA_SOURCE_FROM_DEVICE = "FromDevice";

    @Override
    public List<DataPoint<T>> asDataPoints(List<JsonNode> responseNodes) {

        JsonNode responseNode = responseNodes.get(0);

        List<DataPoint<T>> dataPoints = Lists.newArrayList();

        int measureUnit = asRequiredInteger(responseNode, getUnitPropertyNameForMeasure());

        for (JsonNode listNode : asRequiredNode(responseNode, getListNodeName())) {

            asDataPoint(listNode, measureUnit).ifPresent(dp -> dataPoints.add(dp));
        }

        return dataPoints;
        //        return Collections.singletonList(asDataPoint(responseNode,measureUnit));
    }

    protected DataPointHeader createDataPointHeader(JsonNode listNode, Measure measure) {

        DataPointAcquisitionProvenance.Builder acquisitionProvenanceBuilder =
                new DataPointAcquisitionProvenance.Builder(RESOURCE_API_SOURCE_NAME);

        asOptionalString(listNode, "DataSource").ifPresent(
                dataSource -> setAppropriateModality(dataSource, acquisitionProvenanceBuilder));

        DataPointAcquisitionProvenance acquisitionProvenance = acquisitionProvenanceBuilder.build();

        asOptionalString(listNode, "DataID")
                .ifPresent(externalId -> acquisitionProvenance.setAdditionalProperty("external_id",
                        externalId));

        asOptionalLong(listNode, "LastChangeTime").ifPresent(
                lastUpdatedInUnixSecs -> acquisitionProvenance.setAdditionalProperty("source_updated_date_time",
                        OffsetDateTime.ofInstant(Instant.ofEpochSecond(lastUpdatedInUnixSecs), ZoneId.of("Z"))));

        DataPointHeader dataPointHeader =
                new DataPointHeader.Builder(UUID.randomUUID().toString(), measure.getSchemaId())
                        .setAcquisitionProvenance(acquisitionProvenance)
                        .build();

        return dataPointHeader;
    }

    protected void setEffectiveTimeFrameIfExists(JsonNode listNode, Measure.Builder builder){

        Optional<Long> optionalOffsetDateTime = asOptionalLong(listNode, "MDate");

        if(optionalOffsetDateTime.isPresent()){

            asOptionalString(listNode,"TimeZone").ifPresent(timezoneOffsetString -> builder
                    .setEffectiveTimeFrame(
                            OffsetDateTime.ofInstant(
                                    Instant.ofEpochSecond(optionalOffsetDateTime.get()),
                                    ZoneId.of(timezoneOffsetString))));

        }
    }

    protected void setUserNoteIfExists(JsonNode listNode, Measure.Builder builder){

        Optional<String> note = asOptionalString(listNode, "Note");

        if(note.isPresent() && !note.get().isEmpty()){

            builder.setUserNotes(note.get());
        }


    }

    private void setAppropriateModality(String dataSourceValue, DataPointAcquisitionProvenance.Builder builder) {

        if (dataSourceValue.equals(DATA_SOURCE_FROM_DEVICE)) {
            builder.setModality(SENSED);
        }
        else if (dataSourceValue.equals(DATA_SOURCE_MANUAL)) {
            builder.setModality(SELF_REPORTED);
        }
    }

    protected abstract String getListNodeName();

    protected abstract String getUnitPropertyNameForMeasure();

    protected abstract Optional<DataPoint<T>> asDataPoint(JsonNode jsonNode, int measureUnit);
}
