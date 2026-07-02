package com.github.tvbox.newbox.spider.jar;

import java.util.HashSet;
import java.util.Set;

public class FilteringClassLoader extends ClassLoader {
    private final ClassLoader delegate;
    private final Set<String> hiddenPrefixes;

    public FilteringClassLoader(ClassLoader delegate, Set<String> hiddenPrefixes) {
        this.delegate = delegate;
        this.hiddenPrefixes = hiddenPrefixes;
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        for (String prefix : hiddenPrefixes) {
            if (name.equals(prefix) || name.startsWith(prefix + ".")) {
                throw new ClassNotFoundException("Filtered: " + name);
            }
        }
        return delegate.loadClass(name);
    }
}
