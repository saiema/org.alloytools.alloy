package ar.edu.unrc.dc.mutation.util;

import ar.edu.unrc.dc.mutation.visitors.FunctionsCollector;
import edu.mit.csail.sdg.alloy4.ConstList;
import edu.mit.csail.sdg.alloy4.Pair;
import edu.mit.csail.sdg.alloy4.SafeList;
import edu.mit.csail.sdg.ast.Browsable;
import edu.mit.csail.sdg.ast.Command;
import edu.mit.csail.sdg.ast.Expr;
import edu.mit.csail.sdg.ast.Func;
import edu.mit.csail.sdg.parser.CompModule;

import java.util.*;

public class DependencyScanner {

    public static DependencyGraph scanDependencies(CompModule module) {
        TemporalGraph temporalGraph = new TemporalGraph(module.getAllCommands());
        scanFunctions(module.getAllFunc(), module.getAllCommands(), temporalGraph);
        scanAsserts(module.getAllAssertions(), module.getAllCommands(), temporalGraph);
        temporalGraph.merge();
        analyzeCommandComplexity(module.getAllFunc(), module.getAllAssertions(), module.getAllCommands(), temporalGraph);
        DependencyGraph dependencyGraph = new DependencyGraph(module.getAllCommands());
        module.getAllFunc().forEach(f -> {
            dependencyGraph.addDependencies(f, temporalGraph.getRelatedCommands(f));
        });
        module.getAllAssertions().forEach(a -> {
            dependencyGraph.addDependencies(a.b, temporalGraph.getRelatedCommands(a.b));
        });
        for (Command c : temporalGraph.commands) {
            dependencyGraph.setCommandComplexity(c, temporalGraph.commandComplexity.get(c));
        }
        return dependencyGraph;
    }

    private static void scanFunctions(SafeList<Func> functions, ConstList<Command> commands, TemporalGraph temporalGraph) {
        FunctionsCollector functionsCollector = new FunctionsCollector();
        functions.forEach(f -> {
            temporalGraph.funcDependencyGraph.put(f, new LinkedList<>());
            temporalGraph.funcRelatedCommands.put(f, new LinkedList<>());
            for (Func rf : functionsCollector.visitThis(f.getBody())) {
                temporalGraph.addFuncDependency(f, rf);
            }
        });
        commands.stream().filter(c -> !c.check).forEach(c -> {
            String exprName = c.nameExpr.toString();
            getFuncByName(exprName, functions).ifPresent(f -> {temporalGraph.addRelatedCommand(f, c);});
        });
    }

    private static void scanAsserts(ConstList<Pair<String, Expr>> assertions, ConstList<Command> commands, TemporalGraph temporalGraph) {
        FunctionsCollector functionsCollector = new FunctionsCollector();
        assertions.forEach(namedAssertion -> {
            temporalGraph.assertRelatedFunctions.put(namedAssertion.b, new LinkedList<>());
            temporalGraph.assertRelatedCommands.put(namedAssertion.b, new LinkedList<>());
            Expr assertion = namedAssertion.b;
            functionsCollector.visitThis(assertion).forEach(rf -> {
                temporalGraph.addAssertDependency(assertion, rf);
            });
        });
        commands.stream().filter(c -> c.check).forEach(c -> {
            String assertionsName = c.nameExpr.toString();
            getAssertByName(assertionsName, assertions).ifPresent(a -> {temporalGraph.addRelatedCommand(a, c);});
        });
    }

    private static void analyzeCommandComplexity(SafeList<Func> functions, ConstList<Pair<String, Expr>> assertions, ConstList<Command> commands, TemporalGraph temporalGraph) {
        commands.stream().filter(c -> !c.check).forEach(c -> {
            String exprName = c.nameExpr.toString();
            getFuncByName(exprName, functions).ifPresent(f -> {
                temporalGraph.commandComplexity.put(c, temporalGraph.funcDependencyGraph.get(f).size() + 1);
            });
        });
        commands.stream().filter(c -> c.check).forEach(c -> {
            String assertionsName = c.nameExpr.toString();
            getAssertByName(assertionsName, assertions).ifPresent(a -> {
                int complexity = 1;
                List<Func> arf = temporalGraph.assertRelatedFunctions.get(a);
                List<Func> relatedFunctions = new LinkedList<>();
                arf.forEach(f -> {if (!relatedFunctions.contains(f)) relatedFunctions.add(f);});
                for (Func rf : arf) {
                    TemporalGraph.collectRelatedFunctions(rf, relatedFunctions, temporalGraph.funcDependencyGraph);
                }
                complexity += relatedFunctions.size();
                temporalGraph.commandComplexity.put(c, complexity);
            });
        });
    }

    private static Optional<Func> getFuncByName(String name, SafeList<Func> functions) {
        name = cleanLabel(name);
        for (Func f : functions) {
            if (cleanLabel(f.label).compareTo(name) == 0)
                return Optional.of(f);
        }
        return Optional.empty();
    }

    private static Optional<Expr> getAssertByName(String name, ConstList<Pair<String, Expr>> assertions) {
        name = cleanLabel(name);
        for (Pair<String, Expr> namedAssertion : assertions) {
            if (cleanLabel(namedAssertion.a).compareTo(name) == 0)
                return Optional.of(namedAssertion.b);
        }
        return Optional.empty();
    }

    private static String cleanLabel(String label) {
        if (label.startsWith("this/"))
            return label.substring(5);
        return label;
    }

    private static class TemporalGraph {
        private Map<Expr, List<Func>> assertRelatedFunctions;
        private Map<Expr, List<Command>> assertRelatedCommands;
        private Map<Func, List<Func>> funcDependencyGraph;
        private Map<Func, List<Command>> funcRelatedCommands;
        private Map<Command, Integer> commandComplexity;
        private List<Command> commands;

        public TemporalGraph(List<Command> commands) {
            funcDependencyGraph = new HashMap<>();
            funcRelatedCommands = new HashMap<>();
            assertRelatedCommands = new HashMap<>();
            assertRelatedFunctions = new HashMap<>();
            commandComplexity = new HashMap<>();
            this.commands = commands;
        }

        public void addFuncDependency(Func f1, Func f2) {
            List<Func> associatedFunctions;
            if (funcDependencyGraph.containsKey(f1)) {
                associatedFunctions = funcDependencyGraph.get(f1);
            } else {
                associatedFunctions = new LinkedList<>();
                funcDependencyGraph.put(f1, associatedFunctions);
            }
            if (!associatedFunctions.contains(f2))
                associatedFunctions.add(f2);
        }

        public void addRelatedCommand(Func f, Command c) {
            List<Command> associatedCommands;
            if (funcRelatedCommands.containsKey(f)) {
                associatedCommands = funcRelatedCommands.get(f);
            } else {
                associatedCommands = new LinkedList<>();
                funcRelatedCommands.put(f, associatedCommands);
            }
            if (!associatedCommands.contains(c))
                associatedCommands.add(c);
        }

        public void addAssertDependency(Expr a, Func f) {
            List<Func> associatedFunctions;
            if (assertRelatedFunctions.containsKey(a)) {
                associatedFunctions = assertRelatedFunctions.get(a);
            } else {
                associatedFunctions = new LinkedList<>();
                assertRelatedFunctions.put(a, associatedFunctions);
            }
            if (!associatedFunctions.contains(f))
                associatedFunctions.add(f);
        }

        public void addRelatedCommand(Expr a, Command c) {
            List<Command> associatedCommands;
            if (assertRelatedCommands.containsKey(a)) {
                associatedCommands = assertRelatedCommands.get(a);
            } else {
                associatedCommands = new LinkedList<>();
                assertRelatedCommands.put(a, associatedCommands);
            }
            if (!associatedCommands.contains(c))
                associatedCommands.add(c);
        }

        public List<Command> getRelatedCommands(Browsable b) {
            if (b instanceof Expr) {
                return assertRelatedCommands.containsKey(b)?assertRelatedCommands.get(b):new LinkedList<>();
            } else if (b instanceof Func) {
                return funcRelatedCommands.containsKey(b)?funcRelatedCommands.get(b):new LinkedList<>();
            } else {
                throw new IllegalArgumentException("Argument is neither an Expr nor a Func");
            }
        }

        public void merge() {
            mergeFunctionDependencies();
            mergeFunctionRelatedCommands();
            mergeAssertsRelatedCommands();
            mergeFunctionIndirectlyRelatedAssertCommands();
        }

        private void mergeFunctionDependencies() {
            for (Func f : funcDependencyGraph.keySet()) {
                List<Func> relatedFunctions = new LinkedList<>();
                collectRelatedFunctions(f, relatedFunctions);
                funcDependencyGraph.put(f, relatedFunctions);
            }
        }

        private void mergeFunctionRelatedCommands() {
            for (Func f : funcDependencyGraph.keySet()) {
                List<Command> relatedCommands = new LinkedList<>();
                collectRelatedCommands(f, relatedCommands);
                funcRelatedCommands.put(f, relatedCommands);
            }
        }

        private void mergeAssertsRelatedCommands() {
            for (Expr a : assertRelatedFunctions.keySet()) {
                List<Command> collectedCommands = new LinkedList<>();
                for (Func rf : assertRelatedFunctions.get(a)) {
                    for (Command c : funcRelatedCommands.get(rf)) {
                        if (!collectedCommands.contains(c))
                            collectedCommands.add(c);
                    }
                }
                for (Command c : assertRelatedCommands.get(a)) {
                    if (!collectedCommands.contains(c))
                        collectedCommands.add(c);
                }
                assertRelatedCommands.put(a, collectedCommands);
            }
        }

        private void mergeFunctionIndirectlyRelatedAssertCommands() {
            Set<Expr> asserts = assertRelatedCommands.keySet();
            for (Expr a : asserts) {
                for (Func arf : assertRelatedFunctions.get(a)) {
                    List<Command> arfCommands = funcRelatedCommands.get(arf);
                    for (Command aCommands : assertRelatedCommands.get(a)) {
                        if (!arfCommands.contains(aCommands)) {
                            arfCommands.add(aCommands);
                        }
                    }
                }
            }
        }

        private void collectRelatedFunctions(Func f, List<Func> relatedFunctions) {
            collectRelatedFunctions(f, relatedFunctions, funcDependencyGraph);
        }

        private void collectRelatedCommands(Func f, List<Command> relatedCommands) {
            for (Command rc : funcRelatedCommands.get(f)) {
                if (!relatedCommands.contains(rc))
                    relatedCommands.add(rc);
            }
            for (Func rf : funcDependencyGraph.get(f)) {
                collectRelatedCommands(rf, relatedCommands);
            }
        }

        protected static void collectRelatedFunctions(Func f, List<Func> relatedFunctions, Map<Func, List<Func>> funcDependencyGraph) {
            for (Func rf : funcDependencyGraph.get(f)) {
                if (!relatedFunctions.contains(rf)) {
                    relatedFunctions.add(rf);
                    collectRelatedFunctions(rf, relatedFunctions, funcDependencyGraph);
                }
            }
        }

    }

}