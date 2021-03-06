package bp.MRF;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;

/**
 * Created by vaksenov on 23.07.2019.
 */
public class MRF {
    int nodes;
    int messageId;
    double[][] nodePotentials;
    double[][] logNodePotentials;
    ArrayList<Edge> edges;
    ArrayList<Message> messages;
    ArrayList<Message>[] messagesFrom;
    ArrayList<Message>[] messagesTo;
    HashMap<Pair, Message> messagesByIds;
    double[][] logProductIn;

    public class Pair {
        int u, v;

        public Pair(int u, int v) {
            this.u = u;
            this.v = v;
        }

        public int hashCode() {
            return u * nodes + v;
        }

        public boolean equals(Object o) {
            if (!(o instanceof Pair)) {
                return false;
            }
            Pair p = (Pair) o;
            return u == p.u && v == p.v;
        }
    }

    public MRF(int nodes) {
        this.nodes = nodes;
        nodePotentials = new double[nodes][];
        logNodePotentials = new double[nodes][];
        edges = new ArrayList<>();
        messagesFrom = new ArrayList[nodes];
        messagesTo = new ArrayList[nodes];
        for (int i = 0; i < nodes; i++) {
            messagesFrom[i] = new ArrayList<>();
            messagesTo[i] = new ArrayList<>();
        }
        messagesByIds = new HashMap<>();
        logProductIn = new double[nodes][];
    }

    public MRF(int nodes, double[][] potentials) {
        this(nodes);
        nodePotentials = potentials;
        logNodePotentials = new double[potentials.length][potentials[0].length];
        for (int i = 0; i < logNodePotentials.length; i++) {
            for (int j = 0; j < logNodePotentials[i].length; j++) {
                logNodePotentials[i][j] = Math.log(nodePotentials[i][j]);
            }
        }
        for (int i = 0; i < logProductIn.length; i++) {
            logProductIn[i] = new double[potentials[i].length];
        }
    }

    public int getNodes() {
        return nodes;
    }

    public int getNumberOfValues(int v) {
        return nodePotentials[v].length;
    }

    public void setNodePotential(int v, double[] potentials) {
        nodePotentials[v] = potentials;
        logNodePotentials[v] = new double[potentials.length];
        for (int i = 0; i < potentials.length; i++) {
            logNodePotentials[v][i] = Math.log(potentials[i]);
        }
        logProductIn[v] = new double[potentials.length];
    }

    protected Message createMessage(int i, int j, Edge e) {
        Message m = new Message(messageId++, i, j, e);
        m.fromId = messagesFrom[i].size();
        messagesFrom[i].add(m);
        m.toId = messagesTo[j].size();
        messagesTo[j].add(m);
        messagesByIds.put(new Pair(i, j), m);
        for (int valj = 0; valj < nodePotentials[j].length; valj++) {
            logProductIn[j][valj] += m.logMu[valj];
        }
        return m;
    }

    public void addEdge(int i, int j, double[][] phi) {
        Edge e = new Edge(i, j, phi);
        edges.add(e);

        Message m1 = createMessage(i, j, e);
        Message m2 = createMessage(j, i, e);

        m1.reverse = m2;
        m2.reverse = m1;
    }

    public Collection<Message> getMessages() {
        return messagesByIds.values();
    }

    public Collection<Message> getMessagesFrom(int v) {
        return messagesFrom[v];
    }

    public Collection<Message> getMessagesTo(int v) {
        return messagesTo[v];
    }

    public double[] getFutureMessage(Message m) {
        int i = m.i;
        int j = m.j;
        Message reverseMessage = m.reverse;

        double[] result = new double[nodePotentials[j].length];

//        double[] precalc = new double[nodePotentials[i].length];
//        for (int vali = 0; vali < precalc.length; vali++) {
//            precalc[vali] = Math.log(nodePotentials[i][vali])
//                    + (logProductIn[i][vali] - reverseMessage.logMu[vali]);
//        }

        double[] logsIn = new double[nodePotentials[i].length];
        for (int valj = 0; valj < nodePotentials[j].length; valj++) {
            for (int vali = 0; vali < logsIn.length; vali++) {
//                logsIn[vali] = Math.log(m.getPotential(vali, valj)) + Math.log(nodePotentials[i][vali])
//                        + (logProductIn[i][vali] - reverseMessage.logMu[vali]);
                logsIn[vali] = m.getLogPotential(vali, valj) + logNodePotentials[i][vali]
                        + (logProductIn[i][vali] - reverseMessage.logMu[vali]);
//                logsIn[vali] = m.getLogPotential(vali, valj) + precalc[vali];
//                ;for (Message message : messagesTo[i]) {
//                    if (message.i != j) {
//                        logsIn[vali] += message.logMu[vali];
//                    }
//                }
            }
            result[valj] = Utils.logSum(logsIn);
        }
        double logTotalSum = Utils.logSum(result);
        for (int valj = 0; valj < result.length; valj++) {
            result[valj] -= logTotalSum;
        }
        return result;
    }

    public void updateMessage(Message m, double[] newLogMu) {
        int j = m.j;

        for (int valj = 0; valj < nodePotentials[j].length; valj++) {
            logProductIn[j][valj] += -m.logMu[valj] + newLogMu[valj];
            m.logMu[valj] = newLogMu[valj];
        }
//        m.logMu = Arrays.copyOf(newLogMu, newLogMu.length);
    }

    public void copyMessage(Message m, double[] newLogMu) {
        for (int val = 0; val < m.logMu.length; val++){
            m.logMu[val] = newLogMu[val];
        }
    }

    public void updateNodeSum(int i) {
        for (int vali = 0; vali < nodePotentials[i].length; vali++) {
            logProductIn[i][vali] = 0;
        }
        for (Message message: messagesTo[i]) {
            for (int vali = 0; vali < nodePotentials[i].length; vali++) {
                logProductIn[i][vali] += message.logMu[vali];
            }
        }
    }

    public void updateMessage(Message e) {
        updateMessage(e, getFutureMessage(e));
    }

    public double evaluateEnergy(int[] nodeValues) {
        double log = 0;
        for (int i = 0; i < nodes; i++) {
            log += logNodePotentials[i][nodeValues[i]];
        }
        for (Edge e : edges) {
            log += e.getLogPotential(e.u, e.v, nodeValues[e.u], nodeValues[e.v]);
        }
        return Math.exp(log);
    }

    public double[][] getNodeProbabilities() {
        double[][] answer = new double[nodes][];
        for (int i = 0; i < nodes; i++) {
            answer[i] = new double[nodePotentials[i].length];
            for (int vali = 0; vali < answer[i].length; vali++) {
                answer[i][vali] = logNodePotentials[i][vali] + logProductIn[i][vali];
            }
            double sum = Utils.logSum(answer[i]);
            for (int vali = 0; vali < answer[i].length; vali++) {
                answer[i][vali] -= sum;
                answer[i][vali] = Math.exp(answer[i][vali]);
            }
        }
        return answer;
    }
}
