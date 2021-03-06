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
package com.shaie.browze.resources;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.apache.commons.lang3.StringUtils;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.curator.utils.ZKPaths;
import org.apache.curator.utils.ZKPaths.PathAndNode;
import org.apache.zookeeper.KeeperException.NoNodeException;
import org.apache.zookeeper.data.Stat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.annotation.Timed;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.shaie.browze.model.Tree;
import com.shaie.browze.model.ZkNode;
import com.shaie.browze.model.ZooStatus;

import jersey.repackaged.com.google.common.collect.ImmutableList;

@Path("/zoo")
@Produces(MediaType.APPLICATION_JSON)
public class ZooResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(ZooResource.class);

    private CuratorFramework curatorFramework;

    @Path("status")
    @GET
    @Timed
    public Response status() {
        final ZooStatus status;
        if (curatorFramework != null) {
            status = new ZooStatus(curatorFramework.getZookeeperClient().getCurrentConnectionString());
        } else {
            status = new ZooStatus(null);
        }

        return Response.ok().entity(status).build();
    }

    @Path("connect/{connectString}")
    @GET
    @Timed
    public Response connect(@PathParam("connectString") final String connectString) {
        if (curatorFramework != null) {
            disconnectFromZk();
        }

        connectToZk(connectString);
        return Response.ok()
                .entity(ImmutableMap.of("msg", "Successfully connected to ZooKeeper at " + connectString))
                .build();
    }

    @Path("browse/{path:.*}")
    @GET
    @Timed
    public Response browse(@PathParam("path") final String path,
            @DefaultValue("false") @QueryParam("full_hierarchy") boolean fullHierarchy) throws Exception {
        if (curatorFramework == null) {
            throw new IllegalStateException("Must first /connect to ZK!");
        }
        final String zkPath = "/" + StringUtils.strip(path, "/");
        try {
            final Stat stat = new Stat();
            final byte[] data = curatorFramework.getData().storingStatIn(stat).forPath(zkPath);
            final Tree tree;
            if (!fullHierarchy) {
                final List<Tree> childNodes = getChildren(zkPath, stat);
                final PathAndNode pathAndNode = ZKPaths.getPathAndNode(zkPath);
                tree = new Tree(pathAndNode, childNodes, stat);
            } else {
                tree = getRootNode();
                buildRecursiveTree(tree, zkPath.substring(1));
            }
            final ZkNode zkNode = new ZkNode(tree, data, stat);
            return Response.ok(zkNode).build();
        } catch (@SuppressWarnings("unused") final NoNodeException e) {
            return Response.status(Status.NOT_FOUND)
                    .type(MediaType.TEXT_PLAIN)
                    .entity("Path not found in ZooKeeper: " + zkPath)
                    .build();
        }
    }

    private void buildRecursiveTree(Tree parent, String path) throws Exception {
        if (path.isEmpty()) {
            return;
        }
        final String label = extractLabel(path);
        final String zkPath = ZKPaths.makePath(ZKPaths.makePath(parent.getParent(), parent.getLabel()), label);
        final Stat stat = curatorFramework.checkExists().forPath(zkPath);
        final Tree node = new Tree(ZKPaths.getPathAndNode(zkPath), getChildren(zkPath, stat), stat);
        final List<Tree> parentChildren = parent.getChildren();
        for (int i = 0; i < parentChildren.size(); i++) {
            final Tree child = parentChildren.get(i);
            if (child.getLabel().equals(label)) {
                parentChildren.set(i, node); // replace the child node
                buildRecursiveTree(node, StringUtils.substring(path, label.length() + 1));
                return;
            }
        }
    }

    private Tree getRootNode() throws Exception {
        final Stat stat = curatorFramework.checkExists().forPath("/");
        if (stat == null) {
            throw new IllegalStateException("Cannot get stat of root node!");
        }

        return new Tree(ZKPaths.getPathAndNode("/"), getChildren("/", stat), stat);
    }

    private List<Tree> getChildren(final String zkPath, final Stat stat) throws Exception {
        if (stat.getNumChildren() == 0) {
            return ImmutableList.of();
        }

        final List<String> children = curatorFramework.getChildren().forPath(zkPath);
        return Lists.transform(children, new Function<String, Tree>() {
            @Override
            public Tree apply(String input) {
                try {
                    final Stat childStat = curatorFramework.checkExists().forPath(ZKPaths.makePath(zkPath, input));
                    return new Tree(new PathAndNode(zkPath, input), ImmutableList.<Tree> of(), childStat);
                } catch (final Exception e) {
                    // not expected
                    throw new RuntimeException(e);
                }
            }
        });
    }

    private void connectToZk(String connectString) {
        LOGGER.info("Connecting to ZooKeeper at [{}]", connectString);
        this.curatorFramework = CuratorFrameworkFactory.builder()
                .connectString(connectString)
                .connectionTimeoutMs(30000)
                .retryPolicy(new ExponentialBackoffRetry(1000, 3))
                .build();
        curatorFramework.start();
        try {
            LOGGER.info("Blocking until connection with ZooKeeper is established");
            if (!curatorFramework.blockUntilConnected(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException(String.format(Locale.ROOT,
                        "Failed to establish connection with ZooKeeper at [%s] for 10 seconds", connectString));
            }
            LOGGER.info("Started CuratorFramework");
        } catch (final InterruptedException e) {
            throw new IllegalStateException("Interrupted while waiting for connection with ZooKeeper", e);
        }
    }

    private void disconnectFromZk() {
        if (curatorFramework != null) {
            LOGGER.info("Disconnecting from ZooKeeper at [{}]",
                    curatorFramework.getZookeeperClient().getCurrentConnectionString());
            curatorFramework.close();
            curatorFramework = null;
        }
    }

    private static String extractLabel(String path) {
        int idx = path.indexOf('/');
        if (idx == -1) {
            idx = path.length();
        }
        final String label = path.substring(0, idx);
        return label.isEmpty() ? "/" : label;
    }

}