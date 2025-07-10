package com.republicate.skorm.config;

import org.apache.commons.lang3.StringUtils;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Predicate;

/**
 * <p>A tailored minimalistic properties file config digester</p>
 *
 * @author Claude Brisson
 */

public class ConfigDigester
{
    public static void setProperties(Object bean, Map properties)
    {
        Map<String, Map<String, Object>> subProps = new TreeMap<>();
        for (Map.Entry entry : (Set<Map.Entry>)properties.entrySet())
        {
            String key = (String)entry.getKey();
            Object value = entry.getValue();
            int dot;
            if ((dot = key.indexOf(".")) != -1)
            {
                String subkey = key.substring(0, dot);
                Map<String, Object> submap = subProps.get(subkey);
                if (submap == null)
                {
                    submap = new TreeMap<String, Object>();
                    subProps.put(subkey, submap);
                }
                submap.put(key.substring(dot + 1), value);
            }
            else
            {
                setProperty(bean, (String)entry.getKey(), entry.getValue());
            }
        }
        for (Map.Entry<String, Map<String, Object>> entry : subProps.entrySet())
        {
            setProperty(bean, entry.getKey(), entry.getValue());
        }
    }

    public static void setProperty(Object bean, String name, Object value)
    {
        try
        {
            if (value == null)
            {
                return;
            }

            // search for a getter
            if (value instanceof Map)
            {
                Object subBean = null;
                if (bean instanceof Map map)
                {
                    subBean = map.get(name);
                }
                else
                {
                    String getterName = getGetterName(name);
                    Method getter = findGetter(getterName, bean.getClass(), false);
                    if (getter != null)
                    {
                        subBean = getter.invoke(bean);
                    }
                }
                if (subBean == null)
                {
                    throw new ConfigurationException("Property " + name + " has sub-properties, but target sub-object is null");
                }
                setProperties(subBean, (Map) value);
                return;
            }

            // search for a setter
            String setterName = getSetterName(name);
            Predicate<Class> argumentMatcher = value instanceof String
                ? ConfigDigester::isScalarType
                : value instanceof Map
                ? ConfigDigester::isMapType
                : null;
            if (argumentMatcher == null)
            {
                throw new ConfigurationException("Property " + name + " has unhandled value type " + value.getClass().getName());
            }
            Method setter = findSetter(setterName, bean.getClass(), argumentMatcher, false);
            if (setter == null)
            {
                // search for a map-like put() method
                Class clazz = bean.getClass();
                do
                {
                    for (Method method : clazz.getDeclaredMethods())
                    {
                        // prefix matching: we allow a method name like setWriteAccess for a parameter like write="..."
                        if (method.getParameterCount() == 2 && method.getName().equals("put") && method.getParameterTypes()[0].isAssignableFrom(String.class))
                        {
                            setter = method;
                            break;
                        }
                    }
                    clazz = clazz.getSuperclass();
                }
                while (setter == null && clazz != Object.class);
            }
            if (setter == null)
            {
                throw new ConfigurationException("no setter for property " + name + " on class " + bean.getClass());
            }
            setter.setAccessible(true);
            Class paramClass = setter.getParameterTypes()[setter.getParameterCount() - 1];
            Object argument;
            try
            {
                argument = convertParamToClass(value, paramClass);
            } catch (IllegalArgumentException iae)
            {
                throw new ConfigurationException("cannot convert value to setter argument: " + setterName + "(" + paramClass + ")", iae);
            }
            switch (setter.getParameterCount())
            {
                case 1:
                    setter.invoke(bean, argument);
                    break;
                case 2:
                    setter.invoke(bean, name, argument);
                    break;
                default:
                    throw new ConfigurationException("oops, unhandled case");
            }
        }
        catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e)
        {
            throw new ConfigurationException("could not digest properties", e);
        }
    }

    public static String getGetterName(String name)
    {
        String[] parts = StringUtils.split(name, "_-");
        StringBuilder builder = new StringBuilder("get");
        for (String part : parts)
        {
            builder.append(StringUtils.capitalize(part));
        }
        return builder.toString();
    }

    public static String getSetterName(String name)
    {
        String[] parts = StringUtils.split(name, "_-.");
        StringBuilder builder = new StringBuilder("set");
        for (String part : parts)
        {
            builder.append(StringUtils.capitalize(part));
        }
        return builder.toString();
    }

    private static Set<Class> scalarTypes = new HashSet<>(Arrays.asList(
        String.class, Boolean.TYPE, Short.TYPE, Integer.TYPE, Long.TYPE, Float.TYPE, Double.TYPE
    ));

    public static boolean isScalarType(Class typeClass)
    {
        // also allows arrays of scalar types (vararg)
        return scalarTypes.contains(typeClass)
            || Enum.class.isAssignableFrom(typeClass)
            || (typeClass.isArray() && (
            scalarTypes.contains(typeClass.getComponentType())
                || Enum.class.isAssignableFrom(typeClass.getComponentType())
        ));
    }

    public static boolean isMapType(Class typeClass)
    {
        return Map.class.isAssignableFrom(typeClass);
    }

    public static Method findGetter(String getterName, Class clazz) throws NoSuchMethodException
    {
        return findGetter(getterName, clazz, true);
    }

    public static Method findGetter(String getterName, Class clazz, boolean mandatory) throws NoSuchMethodException
    {
        do
        {
            for (Method method : clazz.getDeclaredMethods())
            {
                // prefix matching: we allow a method name like setWriteAccess for a parameter like write="..."
                if (method.getParameterCount() == 0 && method.getName().startsWith(getterName))
                {
                    return method;
                }
            }
            clazz = clazz.getSuperclass();
        }
        while (clazz != Object.class);
        if (mandatory)
        {
            throw new NoSuchMethodException(clazz.getName() + "::" + getterName);
        }
        else
        {
            return null;
        }
    }

    public static Method findSetter(String setterName, Class clazz) throws NoSuchMethodException
    {
        return findSetter(setterName, clazz, x -> true);
    }

    public static Method findSetter(String setterName, Class clazz, Predicate<Class> argumentClassFilter) throws NoSuchMethodException
    {
        return findSetter(setterName, clazz, argumentClassFilter, true);
    }

    public static Method findSetter(String setterName, Class clazz, Predicate<Class> argumentClassFilter, boolean mandatory) throws NoSuchMethodException
    {
        do
        {
            for (Method method : clazz.getDeclaredMethods())
            {
                // prefix matching: we allow a method name like setWriteAccess for a parameter like write="..."
                if (method.getParameterCount() == 1 && method.getName().startsWith(setterName) && argumentClassFilter.test(method.getParameterTypes()[0]))
                {
                    return method;
                }
            }
            clazz = clazz.getSuperclass();
        }
        while (clazz != Object.class);
        if (mandatory)
        {
            throw new NoSuchMethodException(clazz.getName() + "::" + setterName);
        }
        else
        {
            return null;
        }
    }

    public static Object convertParamToClass(Object value, Class clazz)
    {
        Object ret;
        if (clazz == String.class || clazz == Object.class || Map.class.isAssignableFrom(clazz))
        {
            ret = value;
        }
        else if (clazz == Boolean.TYPE)
        {
            ret = toBoolean(value);
        }
        else if (Enum.class.isAssignableFrom(clazz) && value instanceof String)
        {
            ret = Enum.valueOf(clazz, ((String)value).toUpperCase());
        }
        else if (clazz.isArray())
        {
            Class componentClass = clazz.getComponentType();
            if (value instanceof String)
            {
                String args[] = ((String)value).split(",");
                ret = Array.newInstance(componentClass, args.length);
                for (int i = 0; i < args.length; ++i)
                {
                    Array.set(ret, i, convertParamToClass(args[i].trim(), componentClass));
                }
            }
            else
            {
                ret = Array.newInstance(componentClass, 1);
                Array.set(ret, 0, convertParamToClass(value, componentClass));
            }
        }
        else
        {
            throw new IllegalArgumentException("value cannot be converted to " + clazz.getSimpleName() + " : " + value);
        }
        return ret;
    }

    public static Boolean toBoolean(Object value)
    {
        if (value instanceof Boolean)
        {
            return (Boolean)value;
        }

        String s = toString(value);
        return (s != null) ? Boolean.valueOf(s) : null;
    }

    public static String toString(Object value)
    {
        if (value instanceof String)
        {
            return (String)value;
        }
        if (value == null)
        {
            return null;
        }
        if (value.getClass().isArray())
        {
            if (Array.getLength(value) > 0)
            {
                // recurse on the first value
                return toString(Array.get(value, 0));
            }
            return null;
        }
        return String.valueOf(value);
    }

}
