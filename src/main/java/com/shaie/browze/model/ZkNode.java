/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.shaie.browze.model;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.apache.zookeeper.data.Stat;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Charsets;

public class ZkNode {

    @JsonProperty("tree")
    private final Tree tree;

    @JsonProperty("data")
    private final Object data;

    @JsonProperty("stat")
    private final Stat stat;

    public ZkNode(Tree tree, byte[] data, Stat stat) {
        this.tree = tree;
        this.data = resolveData(data);
        this.stat = stat;
    }

    public Tree getTree() {
        return tree;
    }

    public Object getData() {
        return data;
    }

    public Stat getStat() {
        return stat;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("treeInfo", tree)
                .append("data", data)
                .append("stat", stat)
                .build();
    }

    private static Object resolveData(byte[] data) {
        if (data == null) {
            return null;
        }

        if (data.length == 0) {
            return "";
        }

        return new String(data, Charsets.UTF_8);
    }

}