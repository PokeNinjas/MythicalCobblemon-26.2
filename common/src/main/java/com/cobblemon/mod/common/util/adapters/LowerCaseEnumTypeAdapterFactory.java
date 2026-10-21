package com.cobblemon.mod.common.util.adapters;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Keeps the lowercase enum representation used by Cobblemon resource JSON. */
public final class LowerCaseEnumTypeAdapterFactory implements TypeAdapterFactory {
    @Override
    public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
        Class<? super T> raw = type.getRawType();
        if (!raw.isEnum()) return null;
        Map<String, T> values = new HashMap<>();
        for (Object constant : raw.getEnumConstants()) {
            @SuppressWarnings("unchecked") T value = (T) constant;
            values.put(((Enum<?>) constant).name().toLowerCase(Locale.ROOT), value);
        }
        return new TypeAdapter<T>() {
            @Override public void write(JsonWriter output, T value) throws IOException {
                output.value(((Enum<?>) value).name().toLowerCase(Locale.ROOT));
            }
            @Override public T read(JsonReader input) throws IOException {
                return values.get(input.nextString().toLowerCase(Locale.ROOT));
            }
        }.nullSafe();
    }
}
