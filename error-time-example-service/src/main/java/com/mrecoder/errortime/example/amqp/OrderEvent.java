package com.mrecoder.errortime.example.amqp;

import java.util.List;

/**
 * Example inbound event for {@link OrderEventListener} - stands in for a real
 * domain message. {@code resourceIds} models an order referencing several
 * downstream resources (e.g. line items) that need to be resolved as part of
 * processing; it may be {@code null} or empty for events with nothing to
 * fetch.
 *
 * <p>Deliberately not {@code Serializable}: {@link RabbitTopologyConfig}
 * configures a {@code Jackson2JsonMessageConverter} rather than relying on
 * Spring AMQP's default {@code SimpleMessageConverter}, which would fall
 * back to Java native serialization for a {@code Serializable} payload -
 * {@code ObjectInputStream.readObject()} on anything arriving from a queue
 * is a deserialization-gadget-chain RCE risk (CWE-502) the moment the broker
 * is reachable by anyone untrusted.
 */
public record OrderEvent(String orderId, List<String> resourceIds, String payload) {
}
