package com.zifang.z.mist.core.support;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 单测支撑: JDK 动态代理 + 反射注入 (零 Mockito, 家法规范 SOP 步骤 8).
 * <p>
 * 按方法名分发返回值并记录调用参数, 未登记的方法返回安全默认值;
 * times()/calls() 替代 Mockito 的 verify/captor.
 */
public final class Proxies {

    private Proxies() {
    }

    public static <T> Builder<T> of(Class<T> iface) {
        return new Builder<>(iface);
    }

    /**
     * 反射注入私有字段 (@Autowired/@Resource 字段无 setter).
     */
    public static void inject(Object target, String fieldName, Object value) {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field f = type.getDeclaredField(fieldName);
                f.setAccessible(true);
                f.set(target, value);
                return;
            } catch (NoSuchFieldException e) {
                type = type.getSuperclass();
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }
        throw new IllegalArgumentException("no field: " + fieldName + " on " + target.getClass());
    }

    public static final class Builder<T> {

        private final Class<T> iface;
        private final Map<String, Function<Object[], Object>> answers = new HashMap<>();
        private final Map<String, List<Object[]>> calls = new HashMap<>();

        private Builder(Class<T> iface) {
            this.iface = iface;
        }

        /** 登记方法返回值 (含抛异常语义由调用方在函数内 throw). */
        public Builder<T> on(String method, Function<Object[], Object> answer) {
            answers.put(method, answer);
            return this;
        }

        /** 登记固定返回值. */
        public Builder<T> onReturn(String method, Object value) {
            answers.put(method, args -> value);
            return this;
        }

        /** 该方法被调用次数 (替代 verify). */
        public int times(String method) {
            List<Object[]> list = calls.get(method);
            return list == null ? 0 : list.size();
        }

        /** 该方法的历次入参 (替代 ArgumentCaptor). */
        public List<Object[]> calls(String method) {
            return calls.computeIfAbsent(method, k -> new ArrayList<>());
        }

        @SuppressWarnings("unchecked")
        public T build() {
            return (T) Proxy.newProxyInstance(iface.getClassLoader(), new Class<?>[]{iface},
                    (proxy, method, args) -> {
                        if (method.getDeclaringClass() == Object.class) {
                            switch (method.getName()) {
                                case "toString":
                                    return "proxy(" + iface.getName() + ")";
                                case "hashCode":
                                    return System.identityHashCode(proxy);
                                case "equals":
                                    return proxy == args[0];
                                default:
                                    return null;
                            }
                        }
                        calls.computeIfAbsent(method.getName(), k -> new ArrayList<>()).add(args);
                        Function<Object[], Object> answer = answers.get(method.getName());
                        if (answer != null) {
                            return answer.apply(args);
                        }
                        return defaultValue(method.getReturnType());
                    });
        }

        private static Object defaultValue(Class<?> type) {
            if (!type.isPrimitive()) {
                return null;
            }
            if (type == boolean.class) {
                return false;
            }
            if (type == long.class) {
                return 0L;
            }
            if (type == double.class) {
                return 0d;
            }
            if (type == float.class) {
                return 0f;
            }
            if (type == short.class) {
                return (short) 0;
            }
            if (type == byte.class) {
                return (byte) 0;
            }
            if (type == char.class) {
                return (char) 0;
            }
            return 0;
        }
    }
}
