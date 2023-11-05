package server;

import org.agrona.LangUtil;

import java.lang.reflect.Field;
import java.nio.channels.Selector;

/**
 * @author lzn
 * @date 2023/11/04 11:41
 * @description
 */
public class SelectorFactory {

    /**
     * Reference to the {@code selectedKeys} field in the {@link Selector} class.
     */
    protected static final Field SELECTED_KEYS_FIELD;

    /**
     * Reference to the {@code publicSelectedKeys} field in the {@link Selector} class.
     */
    protected static final Field PUBLIC_SELECTED_KEYS_FIELD;

    private static final String SELECTOR_IMPL = "sun.nio.ch.SelectorImpl";

    static {
        Field selectKeysField = null;
        Field publicSelectKeysField = null;

        try {
            final Class<?> clazz = Class.forName(SELECTOR_IMPL, false, ClassLoader.getSystemClassLoader());

            if (clazz.isAssignableFrom(Selector.open().getClass())) {
                selectKeysField = clazz.getDeclaredField("selectedKeys");
                selectKeysField.setAccessible(true);

                publicSelectKeysField = clazz.getDeclaredField("publicSelectedKeys");
                publicSelectKeysField.setAccessible(true);
            }
        } catch (final Exception ex) {
            LangUtil.rethrowUnchecked(ex);
        } finally {
            SELECTED_KEYS_FIELD = selectKeysField;
            PUBLIC_SELECTED_KEYS_FIELD = publicSelectKeysField;
        }
    }

    public static NioSelectedKeySet keySet(Selector selector) {
        NioSelectedKeySet selectedKeySet = null;
        if (null != PUBLIC_SELECTED_KEYS_FIELD) {
            selectedKeySet = new NioSelectedKeySet();
            try {
                SELECTED_KEYS_FIELD.set(selector, selectedKeySet);
                PUBLIC_SELECTED_KEYS_FIELD.set(selector, selectedKeySet);
            } catch (IllegalAccessException e) {
                e.printStackTrace();
                selectedKeySet = null;
            }
        }

        return selectedKeySet;
    }
}
