package com.ivomens.mcheli.core.content.schema;

import java.util.ArrayList;
import java.util.List;

/** A list the emitter writes on one line, {@code [1.0, 0.5, 0.25, 1.0]}, for short tuples such as a colour. */
public final class InlineList extends ArrayList<Object> {

    private static final long serialVersionUID = 2894262938398431620L;

    public InlineList(List<?> values) {
        super(values);
    }
}
