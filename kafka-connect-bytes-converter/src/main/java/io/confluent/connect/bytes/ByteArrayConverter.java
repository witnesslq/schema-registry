package io.confluent.connect.bytes;

import io.confluent.connect.avro.AvroData;
import io.confluent.kafka.serializers.NonRecordContainer;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.SchemaAndValue;
import org.apache.kafka.connect.errors.DataException;
import org.apache.kafka.connect.storage.Converter;

/**
 * {@link Converter} implementation that only supports serializing to strings. When converting Kafka Connect data to bytes,
 * the schema will be ignored and {@link Object#toString()} will always be invoked to convert the data to a String.
 * When converting from bytes to Kafka Connect format, the converter will only ever return an optional string schema and
 * a string or null.
 *
 * Encoding configuration is identical to {@link StringSerializer} and {@link StringDeserializer}, but for convenience
 * this class can also be configured to use the same encoding for both encoding and decoding with the converter.encoding
 * setting.
 */
public class ByteArrayConverter implements Converter {
	private final ByteArraySerializer serializer = new ByteArraySerializer();
	private final ByteArrayDeserializer deserializer = new ByteArrayDeserializer();

	private AvroData avroData;

	public ByteArrayConverter() {
	}

	@Override
	public void configure(Map<String, ?> configs, boolean isKey) {
		Map<String, Object> serializerConfigs = new HashMap<>();
		serializerConfigs.putAll(configs);
		Map<String, Object> deserializerConfigs = new HashMap<>();
		deserializerConfigs.putAll(configs);

		Object encodingValue = configs.get("converter.encoding");
		if (encodingValue != null) {
			serializerConfigs.put("serializer.encoding", encodingValue);
			deserializerConfigs.put("deserializer.encoding", encodingValue);
		}

		serializer.configure(serializerConfigs, isKey);
		deserializer.configure(deserializerConfigs, isKey);

		avroData = new AvroData(1000);
	}

	@Override
	public byte[] fromConnectData(String topic, Schema schema, Object value) {
		try {
			return serializer.serialize(topic, value == null ? null : (byte[]) value);
		} catch (SerializationException e) {
			throw new DataException("Failed to serialize to a byte[]: ", e);
		}
	}

	@Override
	public SchemaAndValue toConnectData(String topic, byte[] value) {
		try {
			if (value == null) {
				return SchemaAndValue.NULL;
			} else {
				NonRecordContainer stringAvroData = new NonRecordContainer(
						org.apache.avro.Schema.create(org.apache.avro.Schema.Type.BYTES), ByteBuffer.wrap(deserializer
								.deserialize(topic, value)));
				return avroData.toConnectData(stringAvroData.getSchema(), stringAvroData.getValue());
			}
		} catch (SerializationException e) {
			throw new DataException("Failed to deserialize string: ", e);
		}
	}

}
