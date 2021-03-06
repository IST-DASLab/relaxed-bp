package bp.testing;

import bp.MRF.ExamplesMRF;
import bp.MRF.MRF;
import bp.algorithms.*;

import java.io.*;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Locale;

/**
 * Created by vaksenov on 24.07.2019.
 */
public class Main {
    public static void main(String[] args) {
        MRF mrf;
        int size = Integer.parseInt(args[2]);
        int threads = args.length < 3 ? 1 : Integer.parseInt(args[3]);
        switch (args[1]) {
            case "ising":
                mrf = ExamplesMRF.IsingMRF(size, 2, 1);
                break;
            case "potts":
                mrf = ExamplesMRF.PottsMRF(size, 5, 1);
                break;
            case "chain":
                mrf = ExamplesMRF.chain(size, 5, 1);
                break;
            case "ising_chain":
                mrf = ExamplesMRF.IsingMRF(1, size, 2, 1);
                break;
            case "deterministic_chain":
                mrf = ExamplesMRF.deterministicChain(size);
                break;
            case "tree":
                mrf = ExamplesMRF.randomTree(size, 5, 1);
                break;
            case "deterministic_tree":
                mrf = ExamplesMRF.deterministicTree(size);
                break;
            case "ldpc":
                mrf = ExamplesMRF.LDPCCodes(size, 3, 6, 0.07, 1);
                break;
            case "example":
                mrf = ExamplesMRF.residualPaperExample();
                break;
            default:
                throw new AssertionError(String.format("MRF %s is not supported", args[1]));
        }

        System.out.println("The model has been set up");

        double sensitivity = 1e-5;

        BPAlgorithm algorithm = BPAlgorithm.getAlgorithm(args[0], mrf, sensitivity,
                threads, (String[]) Arrays.copyOfRange(args, 4, args.length));

        System.out.println("The algorithm is set up");

        long start = System.currentTimeMillis();
        double[][] res = algorithm.solve();
        long end = System.currentTimeMillis();

        if (args[1].equals("deterministic_chain") || args[1].equals("deterministic_tree")) {
            for (int i = 0; i < size; i++) {
                if (Math.abs(res[i][0] - 0.1) > 0.001) {
                    System.err.println("Something is wrong with vertex " + i);
                    return;
                }
            }
            System.err.println("Everything is fine");
        } else if (args[1].equals("ldpc")) {
            int nonZeros = 0;
            for (int i = 0; i < size - 3; i++) {
                if (res[i][0] < res[i][1]) {
                    System.err.println("Fuck! " + i + " " + Arrays.toString(res[i]));
                    nonZeros++;
                }
            }
            System.err.println("Number of non zeros: " + nonZeros);
            boolean check = ExamplesMRF.LDPCCodesCheckCorrectness(res);
            if (!check) {
                System.err.println("This is not a codeword!");
            } else {
                System.err.println("This is a codeword");
            }
        }

/*        try {
            PrintWriter out = new PrintWriter(String.format("results/%s-%d-%s-%d.txt", args[0], threads, args[1], size));
            out.println(Arrays.deepToString(res));
            out.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }*/
        System.out.println(String.format("Execution time: %d", end - start));

        String dir = "out/residual/";
        String filename = args[1] + "-" + args[2];

        if (args[0].equals("residual")) {
            if (!new File(dir).exists()) {
                new File(dir).mkdirs();
            }
            if (!new File(dir + "/" + filename).exists()) {
                try {
                    PrintWriter out = new PrintWriter(dir + "/" + filename);
                    for (int i = 0; i < res.length; i++) {
                        for (int j = 0; j < res[i].length; j++) {
                            out.print((j == 0 ? "" : " ") + res[i][j]);
                        }
                        out.println();
                    }
                    out.close();
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }
            }
        } else {
            double[][] jury = new double[res.length][];
            try {
                BufferedReader br = new BufferedReader(new FileReader(dir + "/" + filename));
                for (int i = 0; i < jury.length; i++) {
                    jury[i] = new double[res[i].length];
                    String[] a = br.readLine().split(" ");
                    for (int j = 0; j < jury[i].length; j++) {
                        jury[i][j] = Double.parseDouble(a[j]);
                    }
                }

                double accuracy = 0;
                double accuracyMax = 0;
                for (int i = 0; i < res.length; i++) {
                    double L1 = 0;
                    for (int j = 0; j < res[i].length; j++) {
                        L1 += Math.abs(jury[i][j] - res[i][j]);
                    }
                    accuracy += L1;
                    accuracyMax = Math.max(accuracyMax, L1);
                }
                Locale.setDefault(Locale.US);
                System.out.println(String.format("Accuracy: %f", accuracy / res.length));
                System.out.println(String.format("AccuracyMax: %f", accuracyMax));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}