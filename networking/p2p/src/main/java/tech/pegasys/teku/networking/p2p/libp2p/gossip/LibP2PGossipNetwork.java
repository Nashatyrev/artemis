/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package tech.pegasys.teku.networking.p2p.libp2p.gossip;

import com.google.common.base.Preconditions;
import com.google.protobuf.ByteString;
import io.libp2p.core.Connection;
import io.libp2p.core.PeerId;
import io.libp2p.core.Stream;
import io.libp2p.core.pubsub.PubsubApi;
import io.libp2p.core.pubsub.PubsubApiKt;
import io.libp2p.core.pubsub.PubsubPublisherApi;
import io.libp2p.core.pubsub.PubsubSubscription;
import io.libp2p.core.pubsub.Topic;
import io.libp2p.core.pubsub.ValidationResult;
import io.libp2p.etc.AttributesKt;
import io.libp2p.etc.types.WBytes;
import io.libp2p.etc.util.P2PService.PeerHandler;
import io.libp2p.pubsub.FastIdSeenCache;
import io.libp2p.pubsub.PubsubMessage;
import io.libp2p.pubsub.PubsubRouterMessageValidator;
import io.libp2p.pubsub.SeenCache;
import io.libp2p.pubsub.TTLSeenCache;
import io.libp2p.pubsub.gossip.Gossip;
import io.libp2p.pubsub.gossip.GossipParams;
import io.libp2p.pubsub.gossip.GossipRouter;
import io.libp2p.pubsub.gossip.MisbehaviorReason;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.vertx.core.impl.ConcurrentHashSet;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;
import kotlin.jvm.functions.Function0;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.jetbrains.annotations.NotNull;
import pubsub.pb.Rpc.ControlIHave;
import pubsub.pb.Rpc.ControlIWant;
import pubsub.pb.Rpc.RPC;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.networking.p2p.gossip.GossipNetwork;
import tech.pegasys.teku.networking.p2p.gossip.PreparedGossipMessage;
import tech.pegasys.teku.networking.p2p.gossip.PreparedGossipMessageFactory;
import tech.pegasys.teku.networking.p2p.gossip.TopicChannel;
import tech.pegasys.teku.networking.p2p.gossip.TopicHandler;
import tech.pegasys.teku.networking.p2p.libp2p.LibP2PNodeId;
import tech.pegasys.teku.networking.p2p.libp2p.Libp2pPeerStatistics;
import tech.pegasys.teku.networking.p2p.libp2p.Libp2pPeerStatistics.GossipStats.TopicStats;
import tech.pegasys.teku.networking.p2p.network.GossipConfig;
import tech.pegasys.teku.networking.p2p.peer.NodeId;

public class LibP2PGossipNetwork implements GossipNetwork {

  private static final Logger LOG = LogManager.getLogger();

  private static final PubsubRouterMessageValidator STRICT_FIELDS_VALIDATOR =
      new GossipWireValidator();
  private static final Function0<Long> NULL_SEQNO_GENERATOR = () -> null;

  private final MetricsSystem metricsSystem;
  private final Gossip gossip;
  private final PubsubPublisherApi publisher;
  private final TopicHandlers topicHandlers;

  public static LibP2PGossipNetwork create(
      MetricsSystem metricsSystem,
      GossipConfig gossipConfig,
      PreparedGossipMessageFactory defaultMessageFactory,
      boolean logWireGossip) {

    TopicHandlers topicHandlers = new TopicHandlers();
    Gossip gossip = createGossip(gossipConfig, logWireGossip, defaultMessageFactory, topicHandlers);
    PubsubPublisherApi publisher = gossip.createPublisher(null, NULL_SEQNO_GENERATOR);

    return new LibP2PGossipNetwork(metricsSystem, gossip, publisher, topicHandlers);
  }

  private static Gossip createGossip(
      GossipConfig gossipConfig,
      boolean gossipLogsEnabled,
      PreparedGossipMessageFactory defaultMessageFactory,
      TopicHandlers topicHandlers) {
    GossipParams gossipParams =
        GossipParams.builder()
            .D(gossipConfig.getD())
            .DLow(gossipConfig.getDLow())
            .DHigh(gossipConfig.getDHigh())
            .DLazy(gossipConfig.getDLazy())
            .fanoutTTL(gossipConfig.getFanoutTTL())
            .gossipSize(gossipConfig.getAdvertise())
            .gossipHistoryLength(gossipConfig.getHistory())
            .heartbeatInterval(gossipConfig.getHeartbeatInterval())
            .floodPublish(true)
            .seenTTL(gossipConfig.getSeenTTL())
            .build();

    GossipRouter router =
        new GossipRouter(gossipParams) {

          final SeenCache<Optional<ValidationResult>> seenCache =
              new TTLSeenCache<>(
                  new FastIdSeenCache<>(msg -> msg.getProtobufMessage().getData()),
                  gossipParams.getSeenTTL(),
                  getCurTimeMillis());

          @NotNull
          @Override
          protected SeenCache<Optional<ValidationResult>> getSeenMessages() {
            return seenCache;
          }

          @Override
          protected void notifyIWantComplete(@NotNull PeerHandler peer,
              @NotNull PubsubMessage msg) {
            super.notifyIWantComplete(peer, msg);
            Libp2pPeerStatistics.getStats(peer.getPeerId()).gossip.outCompleteIWantCount
                .incrementAndGet();
            Bytes msgId = Bytes.wrap(msg.getMessageId().getArray());
//            System.out.println("### IWant complete: peerId=" + peer.getPeerId() + ", msgId=" + msgId
//                + ", also received from: " + allMessageIds.get(msgId));
          }

          @Override
          protected void notifyIWantTimeout(@NotNull PeerHandler peer, @NotNull WBytes msgId) {
            super.notifyIWantTimeout(peer, msgId);
            Libp2pPeerStatistics.getStats(peer.getPeerId()).gossip.outMissedIWantCount
                .incrementAndGet();
            Bytes msgIdTru = Bytes.wrap(msgId.getArray());
//            System.out.println(
//                "!!! IWant timeout: peerId=" + peer.getPeerId() + ", msgId=" + msgIdTru
//                    + ", but sent by " + allMessageIds.get(msgIdTru));
          }

          @Override
          protected void notifySeenMessage(@NotNull PeerHandler peer, @NotNull PubsubMessage msg,
              @NotNull Optional<ValidationResult> validationResult) {
            super.notifySeenMessage(peer, msg, validationResult);
            notifyAnyMessage(peer, msg);
          }

          @Override
          protected void notifyUnseenMessage(@NotNull PeerHandler peer,
              @NotNull PubsubMessage msg) {
            super.notifyUnseenMessage(peer, msg);
            notifyAnyMessage(peer, msg);
          }

          @Override
          public void notifyAnyMessage(@NotNull PeerHandler peer, @NotNull PubsubMessage msg) {
            super.notifyAnyMessage(peer, msg);
            peerHandlerMap.put(peer.getPeerId(), peer);
            allMessageIds
                .computeIfAbsent(Bytes.wrap(msg.getMessageId().getArray()), __ -> new Vector<>())
                .add(peer.getPeerId());
          }
        };

    router.setMessageFactory(
        msg -> {
          Preconditions.checkArgument(
              msg.getTopicIDsCount() == 1,
              "Unexpected number of topics for a single message: " + msg.getTopicIDsCount());
          String topic = msg.getTopicIDs(0);
          Bytes payload = Bytes.wrap(msg.getData().toByteArray());

          PreparedGossipMessage preparedMessage =
              topicHandlers
                  .getHandlerForTopic(topic)
                  .map(handler -> handler.prepareMessage(payload))
                  .orElse(defaultMessageFactory.create(topic, payload));

          return new PreparedPubsubMessage(msg, preparedMessage);
        });
    router.setMessageValidator(STRICT_FIELDS_VALIDATOR);

//    ChannelHandler debugHandler =
//        gossipLogsEnabled ? new LoggingHandler("wire.gossip", LogLevel.DEBUG) : null;
    PubsubApi pubsubApi = PubsubApiKt.createPubsubApi(router);

    return new Gossip(router, pubsubApi, new GossipStatsHandler());
  }

  static Map<PeerId, PeerHandler> peerHandlerMap = new ConcurrentHashMap<>();
  static Map<Bytes, List<PeerId>> allMessageIds = new ConcurrentHashMap<>();

  @Sharable
  public static class GossipStatsHandler extends ChannelDuplexHandler {

    Libp2pPeerStatistics getStats(ChannelHandlerContext ctx) {
      Stream p2pStream = ctx.channel().attr(AttributesKt.getSTREAM()).get();
      PeerId peerId = p2pStream.getConnection().secureSession().getRemoteId();
      return Libp2pPeerStatistics.getStats(peerId);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
      super.channelRead(ctx, msg);
      RPC gossipMsg = (RPC) msg;
      Libp2pPeerStatistics stats = getStats(ctx);
      logGossip(stats.peerId, true, msg);
      stats.gossip.inPublishCount.addAndGet(gossipMsg.getPublishCount());
      stats.gossip.inPublishSize.addAndGet(
          gossipMsg.getPublishList().stream().mapToInt(pub -> pub.getData().size()).sum());

      List<Bytes> missingIHave = new ArrayList<>();
      int totalIHaveIds = 0;
      for (ControlIHave controlIHave : gossipMsg.getControl().getIhaveList()) {
        for (ByteString bytes : controlIHave.getMessageIDsList()) {
          Bytes truBytes = Bytes.wrap(bytes.toByteArray());
          totalIHaveIds++;
          if (!allMessageIds.containsKey(truBytes)) {
            missingIHave.add(truBytes);
          }
        }
      }

      stats.gossip.inIHaveTotalCount.addAndGet(totalIHaveIds);
      if (totalIHaveIds > 0) {
        stats.gossip.inIHaveCount.incrementAndGet();
      }
      int totalIWantIds = gossipMsg.getControl().getIwantList().stream()
          .mapToInt(ControlIWant::getMessageIDsCount)
          .sum();
      stats.gossip.inIWantTotalCount.addAndGet(totalIWantIds);

      gossipMsg.getControl().getGraftList().forEach(gMsg -> {
        stats.gossip.getTopicStats(gMsg.getTopicID()).inGraft.incrementAndGet();
      });
      gossipMsg.getControl().getPruneList().forEach(pMsg -> {
        stats.gossip.getTopicStats(pMsg.getTopicID()).inPrune.incrementAndGet();
      });

      gossipMsg.getPublishList().forEach(pMsg -> {
        if (pMsg.getTopicIDsCount() != 1) {
          throw new IllegalArgumentException("Invalid message: " + pMsg);
        }
        String topic = pMsg.getTopicIDs(0);
        TopicStats topicStats = stats.gossip.getTopicStats(topic);
        topicStats.inPublishCount.incrementAndGet();
        topicStats.inPublishSize.addAndGet(pMsg.getData().size());
      });

//      if (!missingIHave.isEmpty()) {
//        System.out.println("=> missing IHAVE: " + stats.peerId + " === " + missingIHave);
//      }
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise)
          throws Exception {
      super.write(ctx, msg, promise);
      RPC gossipMsg = (RPC) msg;
      Libp2pPeerStatistics stats = getStats(ctx);
      logGossip(stats.peerId, false, msg);
      stats.gossip.outPublishCount.addAndGet(gossipMsg.getPublishCount());
      stats.gossip.outPublishSize.addAndGet(
          gossipMsg.getPublishList().stream().mapToInt(pub -> pub.getData().size()).sum());
      List<Bytes> iWantList = new ArrayList<>();
      for (ControlIWant iWant : gossipMsg.getControl().getIwantList()) {
        for (ByteString bytes : iWant.getMessageIDsList()) {
          Bytes truBytes = Bytes.wrap(bytes.toByteArray());
          iWantList.add(truBytes);
        }
      }

      int totalIWantIds = gossipMsg.getControl().getIwantList().stream()
          .mapToInt(ControlIWant::getMessageIDsCount)
          .sum();
      int totalIHaveIds = gossipMsg.getControl().getIhaveList().stream()
          .mapToInt(ControlIHave::getMessageIDsCount)
          .sum();
      stats.gossip.outIHaveTotalCount.addAndGet(totalIHaveIds);
      if (totalIHaveIds > 0) {
        stats.gossip.outIHaveCount.incrementAndGet();
      }
      stats.gossip.outIWantTotalCount.addAndGet(totalIWantIds);

      gossipMsg.getControl().getGraftList().forEach(gMsg -> {
        stats.gossip.getTopicStats(gMsg.getTopicID()).outGraft.incrementAndGet();
      });
      gossipMsg.getControl().getPruneList().forEach(pMsg -> {
        stats.gossip.getTopicStats(pMsg.getTopicID()).outPrune.incrementAndGet();
      });

      gossipMsg.getPublishList().forEach(pMsg -> {
        if (pMsg.getTopicIDsCount() != 1) {
          throw new IllegalArgumentException("Invalid message: " + pMsg);
        }
        String topic = pMsg.getTopicIDs(0);
        TopicStats topicStats = stats.gossip.getTopicStats(topic);
        topicStats.outPublishCount.incrementAndGet();
        topicStats.outPublishSize.addAndGet(pMsg.getData().size());
      });

//      if (!iWantList.isEmpty()) {
//        System.out.println("  <= IWANT: toPeer=" + stats.peerId + ", messageIDs: " + iWantList);
//      }
    }


    static Map<PeerId, BufferedWriter> loggers = new ConcurrentHashMap<>();
    static File myLogdir;
    static SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss.SSS");
    static void logGossip(PeerId peer, boolean inbound, Object message) {
      if (myLogdir == null) {
        myLogdir = new File("./gossip.logs");
        myLogdir.mkdirs();
      }
      BufferedWriter writer = loggers.computeIfAbsent(peer, p -> {
        File logFile = new File(myLogdir, p.toBase58() + ".log");
        try {
          return new BufferedWriter(new FileWriter(logFile));
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      });
      try {
        writer.write(dateFormat.format(new Date()) + " ");
        writer.write(inbound ? "   ===> " : " <===   ");
        writer.write(message.toString());
        writer.newLine();
        writer.flush();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
      cause.printStackTrace();
      super.exceptionCaught(ctx, cause);
    }
  };


  public LibP2PGossipNetwork(
      MetricsSystem metricsSystem,
      Gossip gossip,
      PubsubPublisherApi publisher,
      TopicHandlers topicHandlers) {
    this.metricsSystem = metricsSystem;
    this.gossip = gossip;
    this.publisher = publisher;
    this.topicHandlers = topicHandlers;
  }

  @Override
  public SafeFuture<?> gossip(final String topic, final Bytes data) {
    return SafeFuture.of(
        publisher.publish(Unpooled.wrappedBuffer(data.toArrayUnsafe()), new Topic(topic)));
  }

  @Override
  public TopicChannel subscribe(final String topic, final TopicHandler topicHandler) {
    LOG.trace("Subscribe to topic: {}", topic);
    topicHandlers.add(topic, topicHandler);
    final Topic libP2PTopic = new Topic(topic);
    final GossipHandler gossipHandler =
        new GossipHandler(metricsSystem, libP2PTopic, publisher, topicHandler);
    PubsubSubscription subscription = gossip.subscribe(gossipHandler, libP2PTopic);
    return new LibP2PTopicChannel(gossipHandler, subscription);
  }

  @Override
  public Map<String, Collection<NodeId>> getSubscribersByTopic() {
    Map<PeerId, Set<Topic>> peerTopics = gossip.getPeerTopics().join();
    final Map<String, Collection<NodeId>> result = new HashMap<>();
    for (Map.Entry<PeerId, Set<Topic>> peerTopic : peerTopics.entrySet()) {
      final LibP2PNodeId nodeId = new LibP2PNodeId(peerTopic.getKey());
      peerTopic
          .getValue()
          .forEach(
              topic -> result.computeIfAbsent(topic.getTopic(), __ -> new HashSet<>()).add(nodeId));
    }
    return result;
  }

  public Gossip getGossip() {
    return gossip;
  }

  private static class TopicHandlers {

    private final Map<String, TopicHandler> topicToHandlerMap = new ConcurrentHashMap<>();

    public void add(String topic, TopicHandler handler) {
      topicToHandlerMap.put(topic, handler);
    }

    public Optional<TopicHandler> getHandlerForTopic(String topic) {
      return Optional.ofNullable(topicToHandlerMap.get(topic));
    }
  }
}
