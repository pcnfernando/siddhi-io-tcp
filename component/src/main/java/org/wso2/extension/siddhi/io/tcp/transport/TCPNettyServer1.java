/*
 * Copyright (c) 2017, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied. See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.wso2.extension.siddhi.io.tcp.transport;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.apache.log4j.Logger;
import org.wso2.extension.siddhi.io.tcp.transport.callback.StatisticsStreamListener;
import org.wso2.extension.siddhi.io.tcp.transport.callback.StreamListener;
import org.wso2.extension.siddhi.io.tcp.transport.config.ServerConfig;
import org.wso2.extension.siddhi.io.tcp.transport.handlers.EventDecoder;
import org.wso2.extension.siddhi.io.tcp.transport.handlers.EventDecoder1;
import org.wso2.extension.siddhi.io.tcp.transport.utils.StreamTypeHolder;
import org.wso2.siddhi.query.api.definition.Attribute;
import org.wso2.siddhi.query.api.definition.StreamDefinition;

/**
 * TCP Netty Server.
 */
public class TCPNettyServer1 {
    private static final Logger log = Logger.getLogger(TCPNettyServer1.class);
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private StreamTypeHolder streamInfoHolder = new StreamTypeHolder();
    private ChannelFuture channelFuture;
    private String hostAndPort;
//    private FlowController flowController;

    public static void main(String[] args) {
        StreamDefinition streamDefinition = StreamDefinition.id("StockStream").attribute("symbol", Attribute.Type
                .STRING)
                .attribute("price", Attribute.Type.INT).attribute("volume", Attribute.Type.INT);

        TCPNettyServer1 tcpNettyServer = new TCPNettyServer1();
//        tcpNettyServer.addStreamListener(new LogStreamListener(streamDefinition));
        tcpNettyServer.addStreamListener(new StatisticsStreamListener(streamDefinition));

        tcpNettyServer.bootServer(new ServerConfig());
        try {
            Thread.sleep(1000000);
        } catch (InterruptedException e) {
        } finally {
            tcpNettyServer.shutdownGracefully();
        }
    }

    public void bootServer(ServerConfig serverConfig) {
        bossGroup = new NioEventLoopGroup(serverConfig.getReceiverThreads());
        workerGroup = new NioEventLoopGroup(serverConfig.getWorkerThreads());
        hostAndPort = serverConfig.getHost() + ":" + serverConfig.getPort();
        try {
//            flowController = new FlowController(serverConfig.getQueueSizeOfTcpTransport());
            // More terse code to setup the server
            ServerBootstrap bootstrap = new ServerBootstrap();

            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
//                    .childOption(ChannelOption.AUTO_READ, false)
                    .childHandler(new ChannelInitializer() {

                        @Override
                        protected void initChannel(Channel channel) throws Exception {
                            ChannelPipeline p = channel.pipeline();
//                            p.addLast(flowController);
                            p.addLast(new EventDecoder1(streamInfoHolder));
                        }
                    })
                    .option(ChannelOption.TCP_NODELAY, true)          // (5)
                    .childOption(ChannelOption.SO_KEEPALIVE, true); // (6);

            // Bind and start to accept incoming connections.
            channelFuture = bootstrap.bind(serverConfig.getHost(), serverConfig.getPort()).sync();

            log.info("Tcp Server started in " + hostAndPort + "");
        } catch (InterruptedException e) {
            log.error("Error when booting up tcp server on '" + hostAndPort + "' " + e.getMessage(), e);
        }
    }

    public void shutdownGracefully() {
        channelFuture.channel().close();
        try {
            channelFuture.channel().closeFuture().sync();
        } catch (InterruptedException e) {
            log.error("Error when shutdowning the tcp server " + e.getMessage(), e);
        }
        workerGroup.shutdownGracefully();
        bossGroup.shutdownGracefully();
        log.info("Tcp Server running on '" + hostAndPort + "' stopped.");
        workerGroup = null;
        bossGroup = null;

    }

    public synchronized void addStreamListener(StreamListener streamListener) {
        streamInfoHolder.putStreamCallback(streamListener);
    }

    public synchronized void removeStreamListener(String streamId) {
        streamInfoHolder.removeStreamCallback(streamId);
    }

    public synchronized int getNoOfRegisteredStreamListeners() {
        return streamInfoHolder.getNoOfRegisteredStreamListeners();
    }

    public void isPaused(boolean paused) {
//        flowController.isPaused(paused);
    }
}

