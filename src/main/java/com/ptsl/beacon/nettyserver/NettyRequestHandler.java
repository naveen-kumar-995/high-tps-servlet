package com.ptsl.beacon.nettyserver;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.QueryStringDecoder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.List;
import java.util.UUID;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class NettyRequestHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Logger log = LoggerFactory.getLogger(NettyRequestHandler.class);

    // Non-blocking scheduler for delay simulation
    private static final ScheduledExecutorService scheduler =
            Executors.newScheduledThreadPool(Runtime.getRuntime().availableProcessors());

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) {

        String uri = request.uri();
        HttpMethod method = request.method();

        if (log.isDebugEnabled()) {
            log.debug("Incoming request: method={}, uri={}", method, uri);
        }

        try {

            if (uri.startsWith("/api/main/scrubbing-logs") && method.equals(HttpMethod.POST)) {
                if (log.isDebugEnabled()) log.debug("Routing to handleGenerate()");
                handleGenerate(ctx);

            } else if (uri.startsWith("/api/main/scrubbing-logs") && method.equals(HttpMethod.GET)) {
                if (log.isDebugEnabled()) log.debug("Routing to handlePing()");
                handlePing(ctx, uri);

            } else if (uri.equals("/callback/success")) {
                if (log.isDebugEnabled()) log.debug("Routing to handleSuccess()");
                handleSuccess(ctx, request);

            } else if (uri.equals("/callback/fail")) {
                if (log.isDebugEnabled()) log.debug("Routing to handleFail()");
                handleFail(ctx, request);

            } else if (uri.equals("/callback/partial")) {
                if (log.isDebugEnabled()) log.debug("Routing to handlePartial()");
                handlePartial(ctx, request);

            } else if (uri.equals("/callback/batch") && method.equals(HttpMethod.POST)) {
                if (log.isDebugEnabled()) log.debug("Routing to handleBatch()");
                handleBatch(ctx);

            } else if (uri.startsWith("/loadtest/")) {
                if (log.isDebugEnabled()) log.debug("Routing to handleLoadTest()");
                handleLoadTest(ctx, uri);

            } else if (uri.startsWith("/cgi-bin/sendsms")) {
                if (log.isDebugEnabled()) log.debug("Routing to sendsms endpoint");
                sendJson(ctx, 200, Map.of("status", "success"));

            } else {
                if (log.isDebugEnabled()) log.debug("Unknown route: {}", uri);
                sendJson(ctx, 404, Map.of("error", "Not Found"));
            }

        } catch (Exception e) {
            log.error("Error processing request", e);
            sendJson(ctx, 500, Map.of("error", "Internal Server Error"));
        }
    }
    // ================= HANDLERS =================

    private void handleGenerate(ChannelHandlerContext ctx) {
        String uuid = UUID.randomUUID().toString();

        Map<String, Object> response = Map.of(
                "success", true,
                "message", "Scrubbing logged successfully",
                "data", Map.of("authcode", uuid, "dlr_code" , "000")
                                             );

        sendJson(ctx, 200, response);
    }

    private void handlePing(ChannelHandlerContext ctx, String uri) {

        QueryStringDecoder decoder = new QueryStringDecoder(uri);
        String auth = decoder.parameters().getOrDefault("authcode", List.of("")).get(0);

        if ("8b012f6a-87da-404a-9cea-c4b57f33a649".equals(auth)) {
            sendJson(ctx, 403, Map.of("error", "Invalid authcode"));
            return;
        }

        // simulate delay WITHOUT blocking event loop
        scheduler.schedule(() -> {
            Map<String, Object> response = Map.of(
                    "success", true,
                    "message", "Scrubbing logged successfully",
                    "data", Map.of("authCode", auth)
                                                 );
            sendJson(ctx, 200, response);
        }, 100, TimeUnit.MILLISECONDS);
    }

    private void handleSuccess(ChannelHandlerContext ctx, FullHttpRequest req) {
        sendJson(ctx, 200, Map.of(
                "status", "success",
                "count", 1,
                "data", List.of()
                                 ));
    }

    private void handleFail(ChannelHandlerContext ctx, FullHttpRequest req) {
        sendJson(ctx, 200, Map.of(
                "status", "fail",
                "count", 0,
                "data", List.of(Map.of("code", 500))
                                 ));
    }

    private void handlePartial(ChannelHandlerContext ctx, FullHttpRequest req) {
        sendJson(ctx, 200, Map.of(
                "status", "partial",
                "count", 0,
                "data", List.of(
                        Map.of("code", 400),
                        Map.of("code", 500)
                               )
                                 ));
    }

    private void handleBatch(ChannelHandlerContext ctx) {
        scheduler.schedule(() ->
                        sendJson(ctx, 200, Map.of("status", "success")),
                50, TimeUnit.MILLISECONDS
                          );
    }

    private void handleLoadTest(ChannelHandlerContext ctx, String uri) {

        String sleepStr = uri.split("/loadtest/")[1];
        long delay = parseSleep(sleepStr);

        scheduler.schedule(() ->
                        sendJson(ctx, 200, Map.of("status", "success")),
                delay, TimeUnit.MILLISECONDS
                          );
    }

    private long parseSleep(String s) {
        try {
            long val = Long.parseLong(s);
            return Math.min(Math.max(val, 0), 30000);
        } catch (Exception e) {
            return 0;
        }
    }

    // ================= RESPONSE =================

    private void sendJson(ChannelHandlerContext ctx, int status, Object obj) {
        try {
            byte[] json = MAPPER.writeValueAsBytes(obj);

            FullHttpResponse response = new DefaultFullHttpResponse(
                    HttpVersion.HTTP_1_1,
                    HttpResponseStatus.valueOf(status),
                    Unpooled.wrappedBuffer(json)
            );

            response.headers()
                    .set(HttpHeaderNames.CONTENT_TYPE, "application/json")
                    .setInt(HttpHeaderNames.CONTENT_LENGTH, json.length);

            ctx.writeAndFlush(response);

        } catch (Exception e) {
            log.error("Response error", e);
        }
    }
}