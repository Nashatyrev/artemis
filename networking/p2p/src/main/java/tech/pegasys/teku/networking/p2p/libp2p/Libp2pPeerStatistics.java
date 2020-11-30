package tech.pegasys.teku.networking.p2p.libp2p;

import com.google.common.util.concurrent.AtomicDouble;
import io.libp2p.core.PeerId;
import io.libp2p.core.multiformats.Multiaddr;
import io.libp2p.core.multiformats.Protocol;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public class Libp2pPeerStatistics {

  public static final Map<PeerId, Libp2pPeerStatistics> statsMap = new ConcurrentHashMap<>();

  public static Libp2pPeerStatistics getStats(PeerId peer) {
    return statsMap.computeIfAbsent(peer, Libp2pPeerStatistics::new);
  }

  public static List<Libp2pPeerStatistics> getAllConnected() {
    long now = System.currentTimeMillis();
    return statsMap.values().stream()
        .filter(s -> now - s.lastMessageTime < 60_000)
        .sorted(Comparator.comparing(s -> s.lastAgentVersion == null ? "" : s.lastAgentVersion))
        .collect(Collectors.toList());
  }

  public static void printAndClearStats() {
    getAllConnected().forEach(s -> {
      System.out.println(s);
      s.clear();
    });
  }

  public static class ProtocolStats {

    public final String protocol;
    public final AtomicInteger outboundConnected = new AtomicInteger();
    public final AtomicInteger inboundConnected = new AtomicInteger();
    public final AtomicInteger disconnected = new AtomicInteger();

    public final AtomicLong inboundBytes = new AtomicLong();
    public final AtomicLong outboundBytes = new AtomicLong();

    public ProtocolStats(String protocol) {
      this.protocol = protocol;
    }

    void merge(ProtocolStats stats) {
      outboundConnected.addAndGet(stats.outboundConnected.get());
      inboundConnected.addAndGet(stats.inboundConnected.get());
      disconnected.addAndGet(stats.disconnected.get());
      inboundBytes.addAndGet(stats.inboundBytes.get());
      outboundBytes.addAndGet(stats.outboundBytes.get());
    }

    boolean isConnected() {
      return disconnected.get() < outboundConnected.get() + inboundConnected.get();
    }

    @Override
    public String toString() {
      return protocol + " " +
          (isConnected() ? "+" : "-") + "/" + inboundConnected + "/" + outboundConnected
          + ", bytes: " + inboundBytes + "/ " + outboundBytes;
    }
  }

  public static class GossipStats {

    public static class TopicStats {
      public final String topic;

      public TopicStats(String topic) {
        this.topic = topic;
      }

      public final AtomicInteger inGraft = new AtomicInteger();
      public final AtomicInteger inPrune = new AtomicInteger();
      public final AtomicInteger outGraft = new AtomicInteger();
      public final AtomicInteger outPrune = new AtomicInteger();

      public final AtomicInteger inPublishCount = new AtomicInteger();
      public final AtomicLong inPublishSize = new AtomicLong();
      public final AtomicInteger outPublishCount = new AtomicInteger();
      public final AtomicLong outPublishSize = new AtomicLong();

      void clear() {
        inPublishCount.set(0);
        inPublishSize.set(0);
        outPublishCount.set(0);
        outPublishSize.set(0);
      }

      @Override
      public String toString() {
        return "Graft:Prune in/out: " + inGraft + "/" + outGraft + ":" + inPrune + "/" + outPrune + ", " +
            "PublishCount: " + inPublishCount + "/" + outPublishCount + ", " +
            "PublishSize: " + inPublishSize + "/" + outPublishSize + " : " + topic;
      }
    }

    public final AtomicInteger inIWantTotalCount = new AtomicInteger();
    public final AtomicInteger inIHaveTotalCount = new AtomicInteger();
    public final AtomicInteger inIHaveCount = new AtomicInteger();
    public final AtomicInteger inPublishCount = new AtomicInteger();
    public final AtomicLong inPublishSize = new AtomicLong();

    public final AtomicInteger outIWantTotalCount = new AtomicInteger();
    public final AtomicInteger outCompleteIWantCount = new AtomicInteger();
    public final AtomicInteger outMissedIWantCount = new AtomicInteger();
    public final AtomicInteger outIHaveTotalCount = new AtomicInteger();
    public final AtomicInteger outIHaveCount = new AtomicInteger();
    public final AtomicInteger outPublishCount = new AtomicInteger();
    public final AtomicLong outPublishSize = new AtomicLong();

    public final AtomicDouble gossipScore = new AtomicDouble();

    Map<String, TopicStats> topicStatsMap = new ConcurrentHashMap<>();

    void clear() {
      inIWantTotalCount.set(0);
      inIHaveTotalCount.set(0);
      inIHaveCount.set(0);
      inPublishCount.set(0);
      inPublishSize.set(0);
      outIWantTotalCount.set(0);
      outMissedIWantCount.set(0);
      outCompleteIWantCount.set(0);
      outIHaveTotalCount.set(0);
      outIHaveCount.set(0);
      outPublishCount.set(0);
      outPublishSize.set(0);

      topicStatsMap.values().forEach(TopicStats::clear);
    }

    public TopicStats getTopicStats(String topic) {
      return topicStatsMap.computeIfAbsent(topic, TopicStats::new);
    }

    @Override
    public String toString() {
      String ret =
          "Score=" + gossipScore + ", " +
          "IWantTotalCount=" + inIWantTotalCount + "/" + outIWantTotalCount + "/" +
      outCompleteIWantCount + "/" + outMissedIWantCount + ", " +
              "IHaveTotalCount=" + inIHaveTotalCount + "/" + outIHaveTotalCount + ", " +
              "IHaveCount=" + inIHaveCount + "/" + outIHaveCount + ", " +
              "PublishCount=" + inPublishCount + "/" + outPublishCount + ", " +
              "PublishSize=" + inPublishSize + "/" + outPublishSize;
      ret += "\n";
      for (TopicStats value : topicStatsMap.values()) {
        ret += "      " + value + "\n";
      }
      return ret;
    }
  }

  public final PeerId peerId;

  volatile Multiaddr lastConnectedAddr;
  volatile String lastAgentVersion;

  volatile long lastMessageTime;
  volatile long firstConnectedTime;
  volatile long lastConnectedTime;
  volatile long lastDisconnectedTime;

  final AtomicInteger outboundConnected = new AtomicInteger();
  final AtomicInteger inboundConnected = new AtomicInteger();
  final AtomicInteger disconnected = new AtomicInteger();

  final Map<String, ProtocolStats> protocolStats = new ConcurrentHashMap<>();
  public final GossipStats gossip = new GossipStats();

  public Libp2pPeerStatistics(PeerId peerId) {
    this.peerId = peerId;
  }

  void clear() {
    protocolStats.values().forEach(s -> {
      s.outboundBytes.set(0);
      s.inboundBytes.set(0);
    });
    gossip.clear();
  }

  @Override
  public String toString() {
    String ret = peerId.toString() + " " + lastConnectedAddr.getStringComponent(Protocol.IP4) + " "
        + lastAgentVersion + "\n";
    for (ProtocolStats stat : new TreeMap<>(protocolStats).values()) {
      ret += "    " + stat + "\n";
    }
    return ret + "    " + gossip + "\n";
  }
}
