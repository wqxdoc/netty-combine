/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.util;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class DefaultAttributeMapTest {

    private DefaultAttributeMap map;

    @Before
    public void setup() {
        map = new DefaultAttributeMap();
    }

    @Test
    public void testMapExists() {
        assertNotNull(map);
    }

    @Test
    public void testGetSetString() {
        AttributeKey<String> key = AttributeKey.valueOf("Nothing");
        Attribute<String> one = map.attr(key);

        assertSame(one, map.attr(key));

        one.setIfAbsent("Whoohoo");
        assertSame(one.get(), "Whoohoo");

        one.setIfAbsent("What");
        assertNotSame(one.get(), "What");

        one.remove();
        assertNull(one.get());
    }

    @Test
    public void testGetSetInt() {
        AttributeKey<Integer> key = AttributeKey.valueOf("Nada");
        Attribute<Integer> one = map.attr(key);

        assertSame(one, map.attr(key));

        one.setIfAbsent(3653);
        assertEquals(one.get(), Integer.valueOf(3653));

        one.setIfAbsent(1);
        assertNotSame(one.get(), 1);

        one.remove();
        assertNull(one.get());
    }
}
