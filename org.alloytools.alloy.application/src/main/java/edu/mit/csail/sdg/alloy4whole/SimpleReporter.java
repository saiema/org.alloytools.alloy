/* Alloy Analyzer 4 -- Copyright (c) 2006-2009, Felix Chang
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files
 * (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify,
 * merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
 * LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF
 * OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package edu.mit.csail.sdg.alloy4whole;

import ar.edu.unrc.dc.mutation.MutationConfiguration;
import ar.edu.unrc.dc.mutation.MutationConfiguration.ConfigKey;
import ar.edu.unrc.dc.mutation.Ops;
import ar.edu.unrc.dc.mutation.mutantLab.ASTMutator;
import ar.edu.unrc.dc.mutation.mutantLab.Candidate;
import ar.edu.unrc.dc.mutation.mutantLab.MutantLab;
import ar.edu.unrc.dc.mutation.mutantLab.Variabilization;
import ar.edu.unrc.dc.mutation.util.ContextExpressionExtractor;
import ar.edu.unrc.dc.mutation.util.DependencyGraph;
import ar.edu.unrc.dc.mutation.util.DependencyScanner;
import ar.edu.unrc.dc.mutation.util.RepairReport;
import ar.edu.unrc.dc.mutation.visitors.ParentRelationshipFixer;
import ar.edu.unrc.dc.mutation.visitors.NodeAliasingFixer;
import edu.mit.csail.sdg.alloy4.*;
import edu.mit.csail.sdg.alloy4.WorkerEngine.WorkerCallback;
import edu.mit.csail.sdg.alloy4.WorkerEngine.WorkerTask;
import edu.mit.csail.sdg.alloy4viz.StaticInstanceReader;
import edu.mit.csail.sdg.alloy4viz.VizGUI;
import edu.mit.csail.sdg.ast.*;
import edu.mit.csail.sdg.parser.CompModule;
import edu.mit.csail.sdg.parser.CompUtil;
import edu.mit.csail.sdg.translator.*;
import org.alloytools.alloy.core.AlloyCore;

import java.io.*;
import java.util.*;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.stream.Collectors;


/** This helper method is used by SimpleGUI. */

final class SimpleReporter extends A4Reporter {

    public static final class SimpleCallback1 implements WorkerCallback {

        private final SimpleGUI         gui;
        private final VizGUI            viz;
        private final SwingLogPanel     span;
        private final Set<ErrorWarning> warnings = new HashSet<ErrorWarning>();
        private final List<String>      results  = new ArrayList<String>();
        private int                     len2     = 0, len3 = 0, verbosity = 0;
        private final String            latestName;
        private final int               latestVersion;
        //@Atryker - MuAlloy
        public boolean reparing =false;


        public SimpleCallback1(SimpleGUI gui, VizGUI viz, SwingLogPanel span, int verbosity, String latestName, int latestVersion) {
            this.gui = gui;
            this.viz = viz;
            this.span = span;
            this.verbosity = verbosity;
            this.latestName = latestName;
            this.latestVersion = latestVersion;
            len2 = len3 = span.getLength();
        }

        @Override
        public void done() {
            if (viz != null)
                span.setLength(len2);
            else
                span.logDivider();
            span.flush();
            gui.doStop(0);
        }

        @Override
        public void fail() {
            span.logBold("\nAn error has occurred!\n");
            span.logDivider();
            span.flush();
            gui.doStop(1);
        }

        @Override
        public void callback(Object msg) {
            if (msg == null) {
                span.logBold("Done\n");
                span.flush();
                return;
            }
            if (msg instanceof String) {
                span.logBold(((String) msg).trim() + "\n");
                span.flush();
                return;
            }
            if (msg instanceof Throwable) {
                for (Throwable ex = (Throwable) msg; ex != null; ex = ex.getCause()) {
                    if (ex instanceof OutOfMemoryError) {
                        span.logBold("\nFatal Error: the solver ran out of memory!\n" + "Try simplifying your model or reducing the scope,\n" + "or increase memory under the Options menu.\n");
                        return;
                    }
                    if (ex instanceof StackOverflowError) {
                        span.logBold("\nFatal Error: the solver ran out of stack space!\n" + "Try simplifying your model or reducing the scope,\n" + "or increase stack under the Options menu.\n");
                        return;
                    }
                }
            }
            if (msg instanceof Err) {
                Err ex = (Err) msg;
                String text = "fatal";
                boolean fatal = false;
                if (ex instanceof ErrorSyntax)
                    text = "syntax";
                else if (ex instanceof ErrorType)
                    text = "type";
                else
                    fatal = true;
                if (ex.pos == Pos.UNKNOWN)
                    span.logBold("A " + text + " error has occurred:  ");
                else
                    span.logLink("A " + text + " error has occurred:  ", "POS: " + ex.pos.x + " " + ex.pos.y + " " + ex.pos.x2 + " " + ex.pos.y2 + " " + ex.pos.filename);
                if (verbosity > 2) {
                    span.log("(see the ");
                    span.logLink("stacktrace", "MSG: " + ex.dump());
                    span.log(")\n");
                } else {
                    span.log("\n");
                }
                span.logIndented(ex.msg.trim());
                span.log("\n");
                if (fatal && latestVersion > Version.buildNumber())
                    span.logBold("\nNote: You are running Alloy build#" + Version.buildNumber() + ",\nbut the most recent is Alloy build#" + latestVersion + ":\n( version " + latestName + " )\nPlease try to upgrade to the newest version," + "\nas the problem may have been fixed already.\n");
                span.flush();
                if (!fatal)
                    gui.doVisualize("POS: " + ex.pos.x + " " + ex.pos.y + " " + ex.pos.x2 + " " + ex.pos.y2 + " " + ex.pos.filename);
                return;
            }
            if (msg instanceof Throwable) {
                Throwable ex = (Throwable) msg;
                span.logBold(ex.toString().trim() + "\n");
                span.flush();
                return;
            }
            if (!(msg instanceof Object[]))
                return;

            Object[] array = (Object[]) msg;
            //@Atryker - mualloy - Avoid all callback message from the alloy process and only inform Repair messages
            if (reparing) {
                if (array[0].equals("RepairTittle")) {
                    len3 = len2 = span.getLength();
                    span.logBold("" + array[1]);
                }
                if (array[0].equals("RepairSubTittle")) {
                    len3 = len2 = span.getLength();
                    span.log("" + array[1]);
                }

                if (array[0].equals("RepairExprOrig->Mut")) {
                    len3 = len2 = span.getLength();
                    span.log("" + array[1]+":\n");
                    span.log("|<     ");
                    span.logAStrykerGreen(array[2]+"\n");
                    span.log("|>     ");
                    span.logAstrykerBlue(array[3]+"");
                }
                if (array[0].equals("RepairResults")) {
                    len3 = len2 = span.getLength();
                    span.logBold("Results: [" );
                    ArrayList<String> l = (ArrayList<String>) array[1];
                    int i=1;
                    for (String r:l){
                        switch (r){
                            case "E": span.logAstrykerBlue("E");break;
                            case "V": span.logAStrykerGreen("V");break;
                            case "X": span.logAstrykerRed("X");break;
                            default:break;
                        }
                        if (i<l.size()) span.log(",");
                        i++;
                    }
                    span.logBold("]\n\n" );
                }
                return;
            }
            //-----------
            if (array[0].equals("pop")) {
                span.setLength(len2);
                String x = (String) (array[1]);
                if (viz != null && x.length() > 0)
                    OurDialog.alert(x);
            }
            if (array[0].equals("declare")) {
                gui.doSetLatest((String) (array[1]));
            }
            if (array[0].equals("S2")) {
                len3 = len2 = span.getLength();
                span.logBold("" + array[1]);
            }
            if (array[0].equals("R3")) {
                span.setLength(len3);
                span.log("" + array[1]);
            }

            if (array[0].equals("link")) {
                span.logLink((String) (array[1]), (String) (array[2]));
            }
            if (array[0].equals("bold")) {
                span.logBold("" + array[1]);
            }
            if (array[0].equals("")) {
                span.log("" + array[1]);
            }
            if (array[0].equals("scope") && verbosity > 0) {
                span.log("   " + array[1]);
            }
            if (array[0].equals("bound") && verbosity > 1) {
                span.log("   " + array[1]);
            }
            if (array[0].equals("resultCNF")) {
                results.add(null);
                span.setLength(len3);
                span.log("   File written to " + array[1] + "\n\n");
            }
            if (array[0].equals("debug") && verbosity > 2) {
                span.log("   " + array[1] + "\n");
                len2 = len3 = span.getLength();
            }
            if (array[0].equals("translate")) {
                span.log("   " + array[1]);
                len3 = span.getLength();
                span.logBold("   Generating CNF...\n");
            }
            if (array[0].equals("solve")) {
                span.setLength(len3);
                span.log("   " + array[1]);
                len3 = span.getLength();
                span.logBold("   Solving...\n");
            }
            if (array[0].equals("warnings")) {
                if (warnings.size() == 0)
                    span.setLength(len2);
                else if (warnings.size() > 1)
                    span.logBold("Note: There were " + warnings.size() + " compilation warnings. Please scroll up to see them.\n\n");
                else
                    span.logBold("Note: There was 1 compilation warning. Please scroll up to see them.\n\n");
                if (warnings.size() > 0 && Boolean.FALSE.equals(array[1])) {
                    Pos e = warnings.iterator().next().pos;
                    gui.doVisualize("POS: " + e.x + " " + e.y + " " + e.x2 + " " + e.y2 + " " + e.filename);
                    span.logBold("Warnings often indicate errors in the model.\n" + "Some warnings can affect the soundness of the analysis.\n" + "To proceed despite the warnings, go to the Options menu.\n");
                }
            }
            if (array[0].equals("warning")) {
                ErrorWarning e = (ErrorWarning) (array[1]);
                if (!warnings.add(e))
                    return;
                Pos p = e.pos;
                span.logLink("Warning #" + warnings.size(), "POS: " + p.x + " " + p.y + " " + p.x2 + " " + p.y2 + " " + p.filename);
                span.log("\n");
                span.logIndented(e.msg.trim());
                span.log("\n\n");
            }
            if (array[0].equals("sat")) {
                boolean chk = Boolean.TRUE.equals(array[1]);
                int expects = (Integer) (array[2]);
                String filename = (String) (array[3]), formula = (String) (array[4]);
                results.add(filename);
                (new File(filename)).deleteOnExit();
                gui.doSetLatest(filename);
                span.setLength(len3);
                span.log("   ");
                span.logLink(chk ? "Counterexample" : "Instance", "XML: " + filename);
                span.log(" found. ");
                span.logLink(chk ? "Assertion" : "Predicate", formula);
                span.log(chk ? " is invalid" : " is consistent");
                if (expects == 0)
                    span.log(", contrary to expectation");
                else if (expects == 1)
                    span.log(", as expected");
                span.log(". " + array[5] + "ms.\n\n");
            }
            if (array[0].equals("metamodel")) {
                String outf = (String) (array[1]);
                span.setLength(len2);
                (new File(outf)).deleteOnExit();
                gui.doSetLatest(outf);
                span.logLink("Metamodel", "XML: " + outf);
                span.log(" successfully generated.\n\n");
            }
            if (array[0].equals("minimizing")) {
                boolean chk = Boolean.TRUE.equals(array[1]);
                int expects = (Integer) (array[2]);
                span.setLength(len3);
                span.log(chk ? "   No counterexample found." : "   No instance found.");
                if (chk)
                    span.log(" Assertion may be valid");
                else
                    span.log(" Predicate may be inconsistent");
                if (expects == 1)
                    span.log(", contrary to expectation");
                else if (expects == 0)
                    span.log(", as expected");
                span.log(". " + array[4] + "ms.\n");
                span.logBold("   Minimizing the unsat core of " + array[3] + " entries...\n");
            }
            if (array[0].equals("unsat")) {
                boolean chk = Boolean.TRUE.equals(array[1]);
                int expects = (Integer) (array[2]);
                String formula = (String) (array[4]);
                span.setLength(len3);
                span.log(chk ? "   No counterexample found. " : "   No instance found. ");
                span.logLink(chk ? "Assertion" : "Predicate", formula);
                span.log(chk ? " may be valid" : " may be inconsistent");
                if (expects == 1)
                    span.log(", contrary to expectation");
                else if (expects == 0)
                    span.log(", as expected");
                if (array.length == 5) {
                    span.log(". " + array[3] + "ms.\n\n");
                    span.flush();
                    return;
                }
                String core = (String) (array[5]);
                int mbefore = (Integer) (array[6]), mafter = (Integer) (array[7]);
                span.log(". " + array[3] + "ms.\n");
                if (core.length() == 0) {
                    results.add("");
                    span.log("   No unsat core is available in this case. " + array[8] + "ms.\n\n");
                    span.flush();
                    return;
                }
                results.add(core);
                (new File(core)).deleteOnExit();
                span.log("   ");
                span.logLink("Core", core);
                if (mbefore <= mafter)
                    span.log(" contains " + mafter + " top-level formulas. " + array[8] + "ms.\n\n");
                else
                    span.log(" reduced from " + mbefore + " to " + mafter + " top-level formulas. " + array[8] + "ms.\n\n");
            }
            span.flush();
        }
    }

    private void cb(Serializable... objs) {
        cb.callback(objs);
    }

    /** {@inheritDoc} */
    @Override
    public void resultCNF(final String filename) {
        cb("resultCNF", filename);
    }

    /** {@inheritDoc} */
    @Override
    public void warning(final ErrorWarning ex) {
        warn++;
        cb("warning", ex);
    }

    /** {@inheritDoc} */
    @Override
    public void scope(final String msg) {
        cb("scope", msg);
    }

    /** {@inheritDoc} */
    @Override
    public void bound(final String msg) {
        cb("bound", msg);
    }

    /** {@inheritDoc} */
    @Override
    public void debug(final String msg) {
        cb("debug", msg.trim());
    }

    /** {@inheritDoc} */
    @Override
    public void translate(String solver, int bitwidth, int maxseq, int skolemDepth, int symmetry) {
        lastTime = System.currentTimeMillis();
        cb("translate", "Solver=" + solver + " Bitwidth=" + bitwidth + " MaxSeq=" + maxseq + (skolemDepth == 0 ? "" : " SkolemDepth=" + skolemDepth) + " Symmetry=" + (symmetry > 0 ? ("" + symmetry) : "OFF") + '\n');
    }

    /** {@inheritDoc} */
    @Override
    public void solve(final int primaryVars, final int totalVars, final int clauses) {
        minimized = 0;
        cb("solve", "" + totalVars + " vars. " + primaryVars + " primary vars. " + clauses + " clauses. " + (System.currentTimeMillis() - lastTime) + "ms.\n");
        lastTime = System.currentTimeMillis();
    }

    /** {@inheritDoc} */
    @Override
    public void resultSAT(Object command, long solvingTime, Object solution) {
        if (!(solution instanceof A4Solution) || !(command instanceof Command))
            return;
        A4Solution sol = (A4Solution) solution;
        Command cmd = (Command) command;
        String formula = recordKodkod ? sol.debugExtractKInput() : "";
        String filename = tempfile + ".xml";
        synchronized (SimpleReporter.class) {
            try {
                cb("R3", "   Writing the XML file...");
                if (latestModule != null)
                    writeXML(this, latestModule, filename, sol, latestKodkodSRC);
            } catch (Throwable ex) {
                cb("bold", "\n" + (ex.toString().trim()) + "\nStackTrace:\n" + (MailBug.dump(ex).trim()) + "\n");
                return;
            }
            latestKodkods.clear();
            latestKodkods.add(sol.toString());
            latestKodkod = sol;
            latestKodkodXML = filename;
        }
        String formulafilename = "";
        if (formula.length() > 0 && tempfile != null) {
            formulafilename = tempfile + ".java";
            try {
                Util.writeAll(formulafilename, formula);
                formulafilename = "CNF: " + formulafilename;
            } catch (Throwable ex) {
                formulafilename = "";
            }
        }
        cb("sat", cmd.check, cmd.expects, filename, formulafilename, System.currentTimeMillis() - lastTime);
    }

    /** {@inheritDoc} */
    @Override
    public void minimizing(Object command, int before) {
        if (!(command instanceof Command))
            return;
        Command cmd = (Command) command;
        minimized = System.currentTimeMillis();
        cb("minimizing", cmd.check, cmd.expects, before, minimized - lastTime);
    }

    /** {@inheritDoc} */
    @Override
    public void minimized(Object command, int before, int after) {
        minimizedBefore = before;
        minimizedAfter = after;
    }

    /** {@inheritDoc} */
    @Override
    public void resultUNSAT(Object command, long solvingTime, Object solution) {
        if (!(solution instanceof A4Solution) || !(command instanceof Command))
            return;
        A4Solution sol = (A4Solution) solution;
        Command cmd = (Command) command;
        String originalFormula = recordKodkod ? sol.debugExtractKInput() : "";
        String corefilename = "", formulafilename = "";
        if (originalFormula.length() > 0 && tempfile != null) {
            formulafilename = tempfile + ".java";
            try {
                Util.writeAll(formulafilename, originalFormula);
                formulafilename = "CNF: " + formulafilename;
            } catch (Throwable ex) {
                formulafilename = "";
            }
        }
        Pair<Set<Pos>,Set<Pos>> core = sol.highLevelCore();
        if ((core.a.size() > 0 || core.b.size() > 0) && tempfile != null) {
            corefilename = tempfile + ".core";
            OutputStream fs = null;
            ObjectOutputStream os = null;
            try {
                fs = new FileOutputStream(corefilename);
                os = new ObjectOutputStream(fs);
                os.writeObject(core);
                os.writeObject(sol.lowLevelCore());
                corefilename = "CORE: " + corefilename;
            } catch (Throwable ex) {
                corefilename = "";
            } finally {
                Util.close(os);
                Util.close(fs);
            }
        }
        if (minimized == 0)
            cb("unsat", cmd.check, cmd.expects, (System.currentTimeMillis() - lastTime), formulafilename);
        else
            cb("unsat", cmd.check, cmd.expects, minimized - lastTime, formulafilename, corefilename, minimizedBefore, minimizedAfter, (System.currentTimeMillis() - minimized));
    }

    private final WorkerCallback cb;

    // ========== These fields should be set each time we execute a set of
    // commands

    /** Whether we should record Kodkod input/output. */
    private final boolean recordKodkod;

    /**
     * The time that the last action began; we subtract it from
     * System.currentTimeMillis() to determine the elapsed time.
     */
    private long          lastTime  = 0;

    /**
     * If we performed unsat core minimization, then this is the start of the
     * minimization, else this is 0.
     */
    private long          minimized = 0;

    /** The unsat core size before minimization. */
    private int           minimizedBefore;

    /** The unsat core size after minimization. */
    private int           minimizedAfter;

    /**
     * The filename where we can write a temporary Java file or Core file.
     */
    private String        tempfile  = null;

    // ========== These fields may be altered as each successful command
    // generates a Kodkod or Metamodel instance

    /**
     * The set of Strings already enumerated for this current solution.
     */
    private static final Set<String>       latestKodkods      = new LinkedHashSet<String>();

    /**
     * The A4Solution corresponding to the latest solution generated by Kodkod; this
     * field must be synchronized.
     */
    private static A4Solution              latestKodkod       = null;

    /**
     * The root Module corresponding to this.latestKodkod; this field must be
     * synchronized.
     */
    private static Module                  latestModule       = null;

    /**
     * The source code corresponding to the latest solution generated by Kodkod;
     * this field must be synchronized.
     */
    private static ConstMap<String,String> latestKodkodSRC    = null;

    /**
     * The XML filename corresponding to the latest solution generated by Kodkod;
     * this field must be synchronized.
     */
    private static String                  latestKodkodXML    = null;

    /**
     * The XML filename corresponding to the latest metamodel generated by
     * TranslateAlloyToMetamodel; this field must be synchronized.
     */
    private static String                  latestMetamodelXML = null;

    /** Constructor is private. */
    private SimpleReporter(WorkerCallback cb, boolean recordKodkod) {
        this.cb = cb;
        this.recordKodkod = recordKodkod;
    }

    /** Helper method to write out a full XML file. */
    private static void writeXML(A4Reporter rep, Module mod, String filename, A4Solution sol, Map<String,String> sources) throws Exception {
        sol.writeXML(rep, filename, mod.getAllFunc(), sources);
        if (AlloyCore.isDebug())
            validate(filename);
    }

    private int warn = 0;

    /** Task that performs solution enumeration. */
    static final class SimpleTask2 implements WorkerTask {

        private static final long       serialVersionUID = 0;
        public String                   filename         = "";
        public transient WorkerCallback out              = null;

        private void cb(Object... objs) throws Exception {
            out.callback(objs);
        }

        @Override
        public void run(WorkerCallback out) throws Exception {
            this.out = out;
            cb("S2", "Enumerating...\n");
            A4Solution sol;
            Module mod;
            synchronized (SimpleReporter.class) {
                if (latestMetamodelXML != null && latestMetamodelXML.equals(filename)) {
                    cb("pop", "You cannot enumerate a metamodel.\n");
                    return;
                }
                if (latestKodkodXML == null || !latestKodkodXML.equals(filename)) {
                    cb("pop", "You can only enumerate the solutions of the most-recently-solved command.");
                    return;
                }
                if (latestKodkod == null || latestModule == null || latestKodkodSRC == null) {
                    cb("pop", "Error: the SAT solver that generated the instance has exited,\nso we cannot enumerate unless you re-solve that command.\n");
                    return;
                }
                sol = latestKodkod;
                mod = latestModule;
            }
            if (!sol.satisfiable()) {
                cb("pop", "Error: This command is unsatisfiable,\nso there are no solutions to enumerate.");
                return;
            }
            if (!sol.isIncremental()) {
                cb("pop", "Error: This solution was not generated by an incremental SAT solver.\n" + "Currently only MiniSat and SAT4J are supported.");
                return;
            }
            int tries = 0;
            while (true) {
                sol = sol.next();
                if (!sol.satisfiable()) {
                    cb("pop", "There are no more satisfying instances.\n\n" + "Note: due to symmetry breaking and other optimizations,\n" + "some equivalent solutions may have been omitted.");
                    return;
                }
                String toString = sol.toString();
                synchronized (SimpleReporter.class) {
                    if (!latestKodkods.add(toString))
                        if (tries < 100) {
                            tries++;
                            continue;
                        }
                    // The counter is needed to avoid a Kodkod bug where
                    // sometimes we might repeat the same solution infinitely
                    // number of times; this at least allows the user to keep
                    // going
                    writeXML(null, mod, filename, sol, latestKodkodSRC);
                    latestKodkod = sol;
                }
                cb("declare", filename);
                return;
            }
        }
    }

    /**
     * Validate the given filename to see if it is a valid Alloy XML instance file.
     */
    private static void validate(String filename) throws Exception {
        A4SolutionReader.read(new ArrayList<Sig>(), new XMLNode(new File(filename))).toString();
        StaticInstanceReader.parseInstance(new File(filename));
    }

    /** Task that perform one command. */
    static final class SimpleTask1 implements WorkerTask {

        private static final long serialVersionUID = 0;
        public A4Options          options;
        public String             tempdir;
        public boolean            bundleWarningNonFatal;
        public int                bundleIndex;
        public int                resolutionMode;
        public Map<String,String> map;

        public SimpleTask1() {}

        public void cb(WorkerCallback out, Object... objs) throws IOException {
            out.callback(objs);
        }

        @Override
        public void run(WorkerCallback out) throws Exception {
            cb(out, "S2", "Starting the solver...\n\n");
            final SimpleReporter rep = new SimpleReporter(out, options.recordKodkod);
            final Module world = CompUtil.parseEverything_fromFile(rep, map, options.originalFilename, resolutionMode);
            final List<Sig> sigs = world.getAllReachableSigs();
            final ConstList<Command> cmds = world.getAllCommands();
            cb(out, "warnings", bundleWarningNonFatal);
            if (rep.warn > 0 && !bundleWarningNonFatal)
                return;
            List<String> result = new ArrayList<String>(cmds.size());
            if (bundleIndex == -2) {
                final String outf = tempdir + File.separatorChar + "m.xml";
                cb(out, "S2", "Generating the metamodel...\n");
                PrintWriter of = new PrintWriter(outf, "UTF-8");
                Util.encodeXMLs(of, "\n<alloy builddate=\"", Version.buildDate(), "\">\n\n");
                A4SolutionWriter.writeMetamodel(ConstList.make(sigs), options.originalFilename, of);
                Util.encodeXMLs(of, "\n</alloy>");
                Util.close(of);
                if (AlloyCore.isDebug())
                    validate(outf);
                cb(out, "metamodel", outf);
                synchronized (SimpleReporter.class) {
                    latestMetamodelXML = outf;
                }
            } else
                for (int i = 0; i < cmds.size(); i++)
                    if (bundleIndex < 0 || i == bundleIndex) {
                        synchronized (SimpleReporter.class) {
                            latestModule = world;
                            latestKodkodSRC = ConstMap.make(map);
                        }
                        final String tempXML = tempdir + File.separatorChar + i + ".cnf.xml";
                        final String tempCNF = tempdir + File.separatorChar + i + ".cnf";
                        final Command cmd = cmds.get(i);
                        rep.tempfile = tempCNF;
                        cb(out, "bold", "Executing \"" + cmd + "\"\n");
                        A4Solution ai = TranslateAlloyToKodkod.execute_commandFromBook(rep, world.getAllReachableSigs(), cmd, options);
                        if (ai == null)
                            result.add(null);
                        else if (ai.satisfiable())
                            result.add(tempXML);
                        else if (ai.highLevelCore().a.size() > 0)
                            result.add(tempCNF + ".core");
                        else
                            result.add("");
                    }
            (new File(tempdir)).delete(); // In case it was UNSAT, or
                                         // canceled...
            if (result.size() > 1) {
                rep.cb("bold", "" + result.size() + " commands were executed. The results are:\n");
                for (int i = 0; i < result.size(); i++) {
                    Command r = world.getAllCommands().get(i);
                    if (result.get(i) == null) {
                        rep.cb("", "   #" + (i + 1) + ": Unknown.\n");
                        continue;
                    }
                    if (result.get(i).endsWith(".xml")) {
                        rep.cb("", "   #" + (i + 1) + ": ");
                        rep.cb("link", r.check ? "Counterexample found. " : "Instance found. ", "XML: " + result.get(i));
                        rep.cb("", r.label + (r.check ? " is invalid" : " is consistent"));
                        if (r.expects == 0)
                            rep.cb("", ", contrary to expectation");
                        else if (r.expects == 1)
                            rep.cb("", ", as expected");
                    } else if (result.get(i).endsWith(".core")) {
                        rep.cb("", "   #" + (i + 1) + ": ");
                        rep.cb("link", r.check ? "No counterexample found. " : "No instance found. ", "CORE: " + result.get(i));
                        rep.cb("", r.label + (r.check ? " may be valid" : " may be inconsistent"));
                        if (r.expects == 1)
                            rep.cb("", ", contrary to expectation");
                        else if (r.expects == 0)
                            rep.cb("", ", as expected");
                    } else {
                        if (r.check)
                            rep.cb("", "   #" + (i + 1) + ": No counterexample found. " + r.label + " may be valid");
                        else
                            rep.cb("", "   #" + (i + 1) + ": No instance found. " + r.label + " may be inconsistent");
                        if (r.expects == 1)
                            rep.cb("", ", contrary to expectation");
                        else if (r.expects == 0)
                            rep.cb("", ", as expected");
                    }
                    rep.cb("", ".\n");
                }
                rep.cb("", "\n");
            }
            if (rep.warn > 1)
                rep.cb("bold", "Note: There were " + rep.warn + " compilation warnings. Please scroll up to see them.\n");
            if (rep.warn == 1)
                rep.cb("bold", "Note: There was 1 compilation warning. Please scroll up to see it.\n");
        }
    }

    /** Task that perform a repair by muting mutants expressions (marked with #m#) until all commands returns their expected value. */
    static final class SimpleTaskRepair1 implements WorkerTask {

        private static final Logger logger = Logger.getLogger(SimpleTaskRepair1.class.getName());

        static {
            try {
                // This block configure the logger with handler and formatter
                FileHandler fh = new FileHandler("SimpleTaskRepair1.log");
                logger.addHandler(fh);
                SimpleFormatter formatter = new SimpleFormatter();
                fh.setFormatter(formatter);
            } catch (SecurityException | IOException e) {
                e.printStackTrace();
            }
        }

        private static final long serialVersionUID = 0;
        public A4Options          options;
        public String             tempdir;
        public int                resolutionMode;
        public Map<String,String> map;

        public SimpleTaskRepair1() {}

        public void cb(WorkerCallback out, Object... objs) throws IOException {
            out.callback(objs);
        }

        private void setupMutationConfiguration() {
            MutationConfiguration.getInstance().setConfig(ConfigKey.MUTATION_STRICT_TYPE_CHECKING, Boolean.FALSE);              //these lines should be later removed
            MutationConfiguration.getInstance().setConfig(ConfigKey.MUTATION_TOSTRING_FULL, Boolean.FALSE);                     //+
            MutationConfiguration.getInstance().setConfig(ConfigKey.MUTATION_BOUND_MUTATION_BY_ANY_OPERATOR, Boolean.TRUE);     //+
            MutationConfiguration.getInstance().setConfig(ConfigKey.REPAIR_MAX_DEPTH, 3);                                 //+
            MutationConfiguration.getInstance().setConfig(ConfigKey.REPAIR_GENERATOR_CANDIDATE_GETTER_TIMEOUT, 0L);       //++++++++++++++++++++++++++++++++++
            MutationConfiguration.getInstance().setConfig(ConfigKey.REPAIR_DEBUG_SKIP_VERIFICATION, Boolean.FALSE);             //ONLY FOR DEBUGGING MUTATION GENERATION
            MutationConfiguration.getInstance().loadSystemProperties();
            MutationConfiguration.getInstance().setConfig(ConfigKey.REPAIR_VARIABILIZATION, A4Preferences.AStrykerVariabilization.get()); //update the variabilization Repair option
            logger.info(MutationConfiguration.getInstance().toString());
        }

        @Override
        public void run(WorkerCallback out) throws Exception {
            cb(out, "RepairTittle", "Reparing process...\n\n");
            logger.info("Starting repair on model: " + options.originalFilename);
            setupMutationConfiguration();
            final SimpleReporter rep = new SimpleReporter(out, options.recordKodkod);
            final CompModule world = CompUtil.parseEverything_fromFile(rep, map, options.originalFilename, resolutionMode);
            ASTMutator.startInstance(world);
            //==========================================
            fixParentRelationship(world);
            NodeAliasingFixer nodeAliasingFixer = new NodeAliasingFixer();
            nodeAliasingFixer.fixSigNodes(world);
            DependencyGraph dependencyGraph = DependencyScanner.scanDependencies(world);
            // Generate and build the mutation manager
            cb(out, "RepairSubTittle", world.markedEprsToMutate.size()+  " mutations mark detected Executing \n");
            cb(out, "RepairSubTittle", "Generating mutants... ");
            ContextExpressionExtractor.reInitialize(world);
            Variabilization.initializeInstance(null, options);
            Ops[] availableOps = Ops.values();
            logger.info("***Mutation operators***\n" +
                    Arrays.stream(availableOps).filter(Ops::isImplemented).map(Enum::toString).collect(Collectors.joining(",")));
            MutantLab.initialize(world, maxDepthForRepair(), availableOps);
            MutantLab mutantLab = MutantLab.getInstance();
            RepairReport.getInstance().setCommands(DependencyGraph.getInstance().getAllCommands().size());
            RepairReport.getInstance().setVariabilizationRelatedCommands((int) DependencyGraph.getInstance().getAllCommands().stream().filter(Command::isVariabilizationTest).count());
            RepairReport.getInstance().setMarkedExpressions(MutantLab.getInstance().getMarkedExpressions());
            RepairReport.getInstance().clockStart();
            //======================== mutants test cycle ===========
            int count =1;
            while(mutantLab.advance()) {
                cb(out, "RepairSubTittle", "Validating mutant "+ count +" for " +world.getAllCommands().size()+" commands...\n"); count++;
                //report current mutation
                Optional<Candidate> current = mutantLab.getCurrentCandidate();
                if (!current.isPresent())
                    break;
                if (current.get() == Candidate.STOP) {
                    logger.info("Received STOP signal.. stopping search");
                    break;
                }
                current.get().clearMutatedStatus();
                for ( Triplet<String,String,String> em: current.get().getCurrentMutationsInfo()){
                    cb(out, "RepairExprOrig->Mut", em.a, em.b , em.c+"  \n");
                }
                logger.info("Validating mutant");
                logger.info(current.get().toString());
                // check all commands
                boolean discarded = false;
                boolean repaired = true;
                List<String> results = new ArrayList<>();
                List<Command> cmds = MutantLab.getInstance().getCommandsToRunFor(current.get());
                RepairReport.getInstance().incExaminedCandidates();
                for (int i = 0; i < cmds.size(); i++) {
                    logger.info("Running cmd " +  cmds.get(i).toString() + " with complexity " + dependencyGraph.getCommandComplexity(cmds.get(i)));
                    current.get().clearMutatedStatus();
                    try {
                        synchronized (SimpleReporter.class) {
                            latestModule = world;
                            latestKodkodSRC = ConstMap.make(map);
                        }
                        final String tempXML = tempdir + File.separatorChar + i + ".cnf.xml";
                        final String tempCNF = tempdir + File.separatorChar + i + ".cnf";
                        final Command cmd = cmds.get(i);
                        rep.tempfile = tempCNF;
                        //cb(out, "bold", "Executing \"" + cmd + "\"\n");
                        //@mutation ==>> pass the mutation lab to the translator for mutation
                        mutantLab.lockCandidateGeneration();
                        Browsable.freezeParents();
                        //A4Solution ai = skipVerification()?null:TranslateAlloyToKodkod.execute_commandFromBookWithMutation(rep, world.getAllReachableSigs(), cmd, options, current.get());
                        A4Solution ai = evaluateCandidateWithCommand(current.get(), cmd, rep);
                        //if the solution is as expected then continue, else break this mutation checks and continue with other mutation
                        Browsable.unfreezeParents();
                        mutantLab.unlockCandidateGeneration();
                        if (ai != null) {
                            if (ai.satisfiable()) {
                                if (cmd.expects == 0 || (cmd.expects == -1 && cmd.check) ) {
                                    repaired = false;

                                }
                            }
                            else {
                                if (cmd.expects == 1 || (cmd.expects == -1 && !cmd.check) ){
                                    repaired = false;
                                }
                            }
                        } else {
                            repaired = false;
                        }
                        results.add(repaired ? "V":"X");

                    } catch (Exception e) {
                        discarded = true;
                        repaired = false; // if the mutation fails in one command ignore the rest
                        results.add("E");
                        logger.info("ERROR, mutant discarded\ncurrent mutant is:\n"+current.get().toString());
                        StringWriter sw = new StringWriter();
                        e.printStackTrace(new PrintWriter(sw));
                        String exceptionAsString = sw.toString();
                        logger.info(exceptionAsString);
                        current.get().clearMutatedStatus();
                        mutantLab.reportCurrentAsInvalid();
                        Browsable.unfreezeParents();
                        mutantLab.unlockCandidateGeneration();
                    }

                    if (!repaired) break; // if the mutation does not repair for one command of the oracle the ignore the rest of command for it
                }
                //=========== check all result to know if is repaired
                cb(out, "RepairResults", results);
                if (!discarded) {

                    if (!repaired) {
                        rep.cb("bold", "Current mutant does not repair the model, some commands do not results as expected \n");
                        logger.info("Current mutant does not repair the model, some commands do not results as expected");
                    } else {
                        // fix found, break the search
                        current.get().clearMutatedStatus();
                        RepairReport.getInstance().setRepair(current.get());
                        mutantLab.stopSearch();
                        rep.cb("bold", "Current mutant repair the model, all commands results as expected \n");
                        cb(out,  "RepairSubTittle", "Repair:\n" + RepairReport.getInstance().getRepairRepresentation());
                        logger.info("Current mutant repair the model, all commands results as expected");
                        logger.info("MODEL REPAIRED!");
                        break;
                    }
                }

            }
            RepairReport.getInstance().clockEnd();
            logger.info(RepairReport.getInstance().toString());
            mutantLab.stopSearch();
            ASTMutator.destroyInstance();
            DependencyGraph.destroyInstance();
            Variabilization.destroyInstance();
            (new File(tempdir)).delete(); // In case it was UNSAT, or
            // canceled...
        }

        private A4Solution evaluateCandidateWithCommand(Candidate candidate, Command cmd, A4Reporter rep) throws Err {
            if (skipVerification())
                return null;
            return TranslateAlloyToKodkod.execute_commandFromBookWithMutation(rep, candidate.getContext().getAllReachableSigs(), cmd, options, candidate);
        }

        private void fixParentRelationship(CompModule module) {
            for (Func f : module.getAllFunc()) {
                ParentRelationshipFixer parentRelationshipFixer = new ParentRelationshipFixer(f);
                parentRelationshipFixer.fixParentRelation();
            }
            for (Pair<String, Expr> namedAssertion : module.getAllAssertions()) {
                ParentRelationshipFixer parentRelationshipFixer = new ParentRelationshipFixer(namedAssertion.b, module);
                parentRelationshipFixer.fixParentRelation();
            }
            for (Pair<String, Expr> namedFact : module.getAllFacts()) {
                ParentRelationshipFixer parentRelationshipFixer = new ParentRelationshipFixer(namedFact.b, module);
                parentRelationshipFixer.fixParentRelation();
            }
        }

        private boolean skipVerification() {
            Optional<Object> configValue = MutationConfiguration.getInstance().getConfigValue(ConfigKey.REPAIR_DEBUG_SKIP_VERIFICATION);
            return configValue.map(o -> (Boolean) o).orElse((Boolean) ConfigKey.REPAIR_DEBUG_SKIP_VERIFICATION.defaultValue());
        }

        private int maxDepthForRepair() {
            Optional<Object> configValue = MutationConfiguration.getInstance().getConfigValue(ConfigKey.REPAIR_MAX_DEPTH);
            return configValue.map(o -> (Integer) o).orElse((Integer) ConfigKey.REPAIR_MAX_DEPTH.defaultValue());
        }

    }

}
