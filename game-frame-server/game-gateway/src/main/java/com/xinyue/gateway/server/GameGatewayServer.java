package com.xinyue.gateway.server;

import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.xinyue.gateway.config.ServerConfig;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;

@Service
public class GameGatewayServer {

	private EventLoopGroup bossGroup = null;
	private EventLoopGroup workerGroup = null;
	private Logger logger = LoggerFactory.getLogger(GameGatewayServer.class);
	@Autowired
	private ServerConfig serverConfig;

	public void startServer() {
		int port = this.serverConfig.getPort();
		bossGroup = new NioEventLoopGroup(serverConfig.getBossThreads());
		workerGroup = new NioEventLoopGroup(serverConfig.getWorkThreads());
		try {
			ServerBootstrap b = new ServerBootstrap();
			b.group(bossGroup, workerGroup).channel(NioServerSocketChannel.class)
					.childHandler(createChannelInitializer()).option(ChannelOption.SO_BACKLOG, 128)
					.childOption(ChannelOption.SO_KEEPALIVE, true);
			b.childOption(ChannelOption.TCP_NODELAY, true);
			logger.info("开始启动服务，端口:{}", serverConfig.getPort());
			ChannelFuture f = b.bind(port).sync();
			f.channel().closeFuture().sync();
		} catch (InterruptedException e) {
			e.printStackTrace();
		} finally {
			workerGroup.shutdownGracefully();
			bossGroup.shutdownGracefully();
		}
	}

	private ChannelInitializer<Channel> createChannelInitializer() {
		ChannelInitializer<Channel> channelInitializer = new ChannelInitializer<Channel>() {

			@Override
			protected void initChannel(Channel ch) throws Exception {
				ChannelPipeline p = ch.pipeline();
				//添加接收消息包的handler
				p.addLast(new LengthFieldBasedFrameDecoder(1024, 0, 4, -4, 0));
				p.addLast(new GameMessageDecode());
				p.addLast(new GameGatewayConnectHandler());
				p.addLast(new GameMessageEncode());
				p.addLast(new DispatchMessageHandler());
			}
		};
		return channelInitializer;
	}

	public void stop() {
		int quietPeriod = 5;
		int timeout = 30;
		TimeUnit timeUnit = TimeUnit.SECONDS;
		workerGroup.shutdownGracefully(quietPeriod, timeout, timeUnit);
		bossGroup.shutdownGracefully(quietPeriod, timeout, timeUnit);
	}

	public EventLoopGroup getWorkerGroup() {
		return workerGroup;
	}

}
