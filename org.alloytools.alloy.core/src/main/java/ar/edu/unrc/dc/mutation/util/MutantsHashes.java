package ar.edu.unrc.dc.mutation.util;

import ar.edu.unrc.dc.mutation.MutationConfiguration;
import ar.edu.unrc.dc.mutation.mutantLab.Candidate;
import ar.edu.unrc.dc.mutation.visitors.ExprToString;
import edu.mit.csail.sdg.ast.Browsable;
import edu.mit.csail.sdg.ast.Expr;
import edu.mit.csail.sdg.ast.Func;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class MutantsHashes {

    public static boolean logEnable = false;
    private static final Logger logger = Logger.getLogger(MutantsHashes.class.getName());

    static {
        try {
            // This block configure the logger with handler and formatter
            FileHandler fh = new FileHandler("HashesDetection.log");
            logger.addHandler(fh);
            SimpleFormatter formatter = new SimpleFormatter();
            fh.setFormatter(formatter);
        } catch (SecurityException | IOException e) {
            e.printStackTrace();
        }
    }

    private final Set<byte[]> hashes;
    private FileWriter writer;

    public MutantsHashes() {
        hashes = new TreeSet<>((left, right) -> {
            for (int i = 0, j = 0; i < left.length && j < right.length; i++, j++) {
                int a = (left[i] & 0xff);
                int b = (right[j] & 0xff);
                if (a != b) {
                    return a - b;
                }
            }
            return left.length - right.length;
        });
        File chashes = candidateHashesFile();
        try {
            this.writer = chashes == null?null:new FileWriter(chashes);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public boolean add(Candidate c) {
        StringBuilder sb = new StringBuilder();
        for (Browsable relatedFuncAndAssertion : c.getRelatedAssertionsAndFunctions()) {
            ExprToString toString = new ExprToString(c);
            if (relatedFuncAndAssertion instanceof Func) {
                Func relatedFunc = (Func) relatedFuncAndAssertion;
                toString.visitThis(relatedFunc.getBody());
            } else {
                Expr relatedAssertion = (Expr) relatedFuncAndAssertion;
                toString.visitThis(relatedAssertion);
            }
            sb.append(toString.getStringRepresentation()).append("\n");
            c.clearMutatedStatus();
        }
        String stringToHash = sb.toString();
        MessageDigest messageDigest;
        try {
            messageDigest = MessageDigest.getInstance("MD5");
            messageDigest.update(stringToHash.getBytes());
            byte[] digest = messageDigest.digest();
            boolean exists = !hashes.add(digest);
            if (logEnable)
                logger.info("candidate : " + c.toString() + "\nhas hash : " + Arrays.toString(digest) + "\n" + (exists?"Already exists":"New candidate"));
            if (!exists && writer != null) {
                try {
                    writer.write(Arrays.toString(digest) + "\n");
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            return !exists;
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            return false;
        }
    }

    private static File candidateHashesFile() {
        String chashesRaw = (String) MutationConfiguration.getInstance().getConfigValueOrDefault(MutationConfiguration.ConfigKey.HACKS_CANDIDATE_HASHES);
        if (!chashesRaw.trim().isEmpty()) {
            File chashes = new File(chashesRaw);
            if (!chashes.exists()) {
                try {
                    if (chashes.createNewFile()) {
                        return chashes;
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return null;
    }

}
