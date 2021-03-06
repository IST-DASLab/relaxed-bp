package bp.MRF;

import bp.algorithms.queues.IdentifiedClass;

import java.util.Arrays;

/**
 * Created by vaksenov on 23.07.2019.
 */
public class Message extends IdentifiedClass {
    public int i, j;
    Edge e;
    public double[] logMu;
    public Message reverse;

    // For relaxed RMF
    public int fromId, toId;

    public Message(int id, int i, int j, Edge e) {
        this.id = id;
        this.i = i;
        this.j = j;

        this.e = e;
        if (e.u == i) {
            this.logMu = new double[e.potentials[0].length];
        } else {
            this.logMu = new double[e.potentials.length];
        }
        Arrays.fill(logMu, Math.log(1. / logMu.length));
    }

    public double getPotential(int vi, int vj) {
        return e.getPotential(i, j, vi, vj);
    }

    public double getLogPotential(int vi, int vj) {
        return e.getLogPotential(i, j, vi, vj);
    }
}
