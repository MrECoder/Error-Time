package com.mrecoder.errortime.amqp;

import java.io.Serializable;
import java.util.List;

/**
 * Example inbound event for {@link OrderEventListener} - stands in for a real
 * domain message. {@code resourceIds} models an order referencing several
 * downstream resources (e.g. line items) that need to be resolved as part of
 * processing; it may be {@code null} or empty for events with nothing to
 * fetch.
 */
public record OrderEvent(String orderId, List<String> resourceIds, String payload) implements Serializable {
}
