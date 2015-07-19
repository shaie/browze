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

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.apache.curator.utils.ZKPaths.PathAndNode;
import org.apache.zookeeper.data.Stat;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;

public class Tree {

    @JsonProperty("label")
    private final String label;

    @JsonProperty("children")
    private final List<Tree> children;

    @JsonProperty("leaf")
    private final boolean isLeaf;

    @JsonProperty("parent")
    private final String parent;

    public Tree(PathAndNode pathAndNode, List<Tree> children, Stat stat) {
        if (Strings.isNullOrEmpty(pathAndNode.getNode())) {
            // root node
            this.label = pathAndNode.getPath();
            this.parent = null;
        } else {
            this.label = pathAndNode.getNode();
            this.parent = pathAndNode.getPath();
        }
        this.children = Lists.newArrayList(children);
        Collections.sort(this.children, new Comparator<Tree>() {
            @Override
            public int compare(Tree o1, Tree o2) {
                // if one of the nodes is a leaf and one isn't, favor 'directories' first
                if (o1.isLeaf != o2.isLeaf) {
                    return o1.isLeaf ? 1 : -1;
                }

                // both nodes are either leaves or directories, sort by nodeName
                return o1.label.compareTo(o2.label);
            }
        });
        this.isLeaf = children.isEmpty() && stat.getNumChildren() == 0;
    }

    public String getLabel() {
        return label;
    }

    public List<Tree> getChildren() {
        return children;
    }

    public boolean isLeaf() {
        return isLeaf;
    }

    public String getParent() {
        return parent;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("label", label)
                .append("children", children)
                .append("parent", parent)
                .append("leaf", isLeaf)
                .build();
    }

}