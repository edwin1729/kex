package org.vorpal.research.kex.test.javadebug;

import java.util.HashSet;

@SuppressWarnings("ALL")
public class JavaTest {
    public int foo(HashSet<Integer> list) {
        if (list.size() == 1) {
            System.out.println("a");
        }
        return 0;
    }
}

