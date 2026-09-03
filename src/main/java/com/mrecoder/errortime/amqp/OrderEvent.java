package com.mrecoder.errortime.amqp;

import java.io.Serializable;

/** Example inbound event for {@link OrderEventListener} - stands in for a real domain message. */
public record OrderEvent(String orderId, String payload) implements Serializable {
}
