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

package edu.mit.csail.sdg.ast;

import edu.mit.csail.sdg.alloy4.*;
import edu.mit.csail.sdg.alloy4.ConstList.TempList;

import java.util.*;

/**
 * Immutable; reresents a "run" or "check" command.
 * <p>
 * <b>Invariant:</b> expects == -1, 0, or 1
 * <p>
 * <b>Invariant:</b> overall >= -1
 * <p>
 * <b>Invariant:</b> bitwidth >= -1
 * <p>
 * <b>Invariant:</b> maxseq >= -1
 * <p>
 * <b>Invariant:</b> maxstring >= -1
 */

public final class Command extends Browsable {

    public enum TestType {FROM_MODEL, CE, TRUSTED, UNTRUSTED}

    private boolean isVariabilizationTest = false;
    private boolean isPerfectOracleTest = false;
    private boolean isGenerated = false;
    private TestType testType = TestType.FROM_MODEL;
    public boolean isVariabilizationTest() {
        return isVariabilizationTest;
    }
    public void setAsVariabilizationTest() {
        isVariabilizationTest = true;
    }
    public boolean isPerfectOracleTest() { return isPerfectOracleTest; }
    public void setAsPerfectOracleTest() { isPerfectOracleTest = true; }
    public boolean isGenerated() {return isGenerated;}
    public void setAsGenerated() {isGenerated = true;}
    public TestType testType() {
        return testType;
    }
    public void testType(TestType testType) {
        if (testType == null)
            throw new IllegalArgumentException("testType can't be null");
        this.testType = testType;
    }
    private boolean hasFacts = false;
    public void factsAdded() {
        hasFacts = true;
    }
    public boolean hasFacts() {
        return hasFacts;
    }


    /**
     * If nonnull, it means this command depends on this parent command.
     */
    public final Command                 parent;

    /**
     * The position in the original file where this command was declared; never
     * null.
     */
    public final Pos                     pos;

    /**
     * The label for the command; it is just for pretty-printing and does not have
     * to be unique.
     */
    public final String                  label;

    /** true if this is a "check"; false if this is a "run". */
    public final boolean                 check;

    /**
     * The overall scope (0 or higher) (Or -1 if there is no overall scope).
     */
    public final int                     overall;

    /**
     * The integer bitwidth (0 or higher) (Or -1 if it was not specified).
     */
    public final int                     bitwidth;

    /**
     * The maximum sequence length (0 or higher) (Or -1 if it was not specified).
     */
    public final int                     maxseq;

    /**
     * The number of String atoms to allocate (0 or higher) (Or -1 if it was not
     * specified).
     */
    public final int                     maxstring;

    /**
     * The expected answer (either 0 or 1) (Or -1 if there is no expected answer).
     */
    public final int                     expects;

    /** The formula associated with this command. */
    public final Expr                    formula;
    /** The list of scopes. */
    public final ConstList<CommandScope> scope;

    /**
     * This stores a list of Sig whose scope shall be considered "exact", but we may
     * or may not know what its scope is yet.
     */
    public final ConstList<Sig>          additionalExactScopes;

    public final Expr                    nameExpr;

    /**
     * Returns a human-readable string that summarizes this Run or Check command.
     */
    @Override
    public final String toString() {
        if (parent != null) {
            Command p = parent;
            while (p.parent != null)
                p = p.parent;
            return p.toString();
        }
        boolean first = true;
        StringBuilder sb = new StringBuilder(check ? "check " : "run ").append(label);
        if (overall >= 0 && (bitwidth >= 0 || maxseq >= 0 || scope.size() > 0))
            sb.append(" for ").append(overall).append(" but");
        else if (overall >= 0)
            sb.append(" for ").append(overall);
        else if (bitwidth >= 0 || maxseq >= 0 || scope.size() > 0)
            sb.append(" for");
        if (bitwidth >= 0) {
            sb.append(" ").append(bitwidth).append(" int");
            first = false;
        }
        if (maxseq >= 0) {
            sb.append(first ? " " : ", ").append(maxseq).append(" seq");
            first = false;
        }
        for (CommandScope e : scope) {
            sb.append(first ? " " : ", ").append(e);
            first = false;
        }
        if (expects >= 0)
            sb.append(" expect ").append(expects);
        return sb.toString();
    }

    /**
     * Constructs a new Command object.
     *
     * @param check - true if this is a "check"; false if this is a "run"
     * @param overall - the overall scope (0 or higher) (-1 if no overall scope was
     *            specified)
     * @param bitwidth - the integer bitwidth (0 or higher) (-1 if it was not
     *            specified)
     * @param maxseq - the maximum sequence length (0 or higher) (-1 if it was not
     *            specified)
     * @param formula - the formula that must be satisfied by this command
     */
    public Command(boolean check, int overall, int bitwidth, int maxseq, Expr formula) throws ErrorSyntax {
        this(null, null, "", check, overall, bitwidth, maxseq, -1, null, null, formula, null);
    }

    /**
     * Constructs a new Command object.
     *
     * @param check - true if this is a "check"; false if this is a "run"
     * @param overall - the overall scope (0 or higher) (-1 if no overall scope was
     *            specified)
     * @param bitwidth - the integer bitwidth (0 or higher) (-1 if it was not
     *            specified)
     * @param maxseq - the maximum sequence length (0 or higher) (-1 if it was not
     *            specified)
     * @param expects - the expected value (0 or 1) (-1 if no expectation was
     *            specified)
     * @param formula - the formula that must be satisfied by this command
     */
    public Command(boolean check, int overall, int bitwidth, int maxseq, int expects, Expr formula) throws ErrorSyntax {
        this(null, ExprVar.make(null, "NO_NAME"), "NO_NAME", check, overall, bitwidth, maxseq, expects, null, null, formula, null);
    }

    /**
     * Constructs a new Command object.
     *
     * @param pos - the original position in the file (must not be null)
     * @param label - the label for this command (it is only for pretty-printing and
     *            does not have to be unique)
     * @param check - true if this is a "check"; false if this is a "run"
     * @param overall - the overall scope (0 or higher) (-1 if no overall scope was
     *            specified)
     * @param bitwidth - the integer bitwidth (0 or higher) (-1 if it was not
     *            specified)
     * @param maxseq - the maximum sequence length (0 or higher) (-1 if it was not
     *            specified)
     * @param expects - the expected value (0 or 1) (-1 if no expectation was
     *            specified)
     * @param scope - a list of scopes (can be null if we want to use default)
     * @param additionalExactSig - a list of sigs whose scope shall be considered
     *            exact though we may or may not know what the scope is yet
     * @param formula - the formula that must be satisfied by this command
     */
    public Command(Pos pos, Expr e, String label, boolean check, int overall, int bitwidth, int maxseq, int expects, Iterable<CommandScope> scope, Iterable<Sig> additionalExactSig, Expr formula, Command parent) {
        if (pos == null)
            pos = Pos.UNKNOWN;
        this.nameExpr = e;
        this.formula = formula;
        this.pos = pos;
        this.label = (label == null ? "" : label);
        this.check = check;
        this.overall = (overall < 0 ? -1 : overall);
        this.bitwidth = (bitwidth < 0 ? -1 : bitwidth);
        this.maxseq = (maxseq < 0 ? -1 : maxseq);
        this.maxstring = (-1);
        this.expects = (expects < 0 ? -1 : (expects > 0 ? 1 : 0));
        this.scope = ConstList.make(scope);
        this.additionalExactScopes = ConstList.make(additionalExactSig);
        this.parent = parent;
        defineParentForComponents();
    }

    public Expr getFormulaWithoutFacts() {
        if (!hasFacts)
            throw new IllegalStateException("This command has no facts, use #hasFacts() to check before calling this method");
        Expr commandFormula = formula;
        while ((commandFormula instanceof ExprUnary) && ((ExprUnary)commandFormula).op.equals(ExprUnary.Op.NOOP)) {
            commandFormula = ((ExprUnary) commandFormula).sub;
        }
        boolean isAnd = (commandFormula instanceof ExprBinary) && ((ExprBinary) commandFormula).op.equals(ExprBinary.Op.AND);
        if (!isAnd)
            throw new IllegalStateException("The command has facts but the main expression is not an AND expression");
        return ((ExprBinary)commandFormula).right;
    }

    public Expr getFacts() {
        if (!hasFacts)
            throw new IllegalStateException("This command has no facts, use #hasFacts() to check before calling this method");
        Expr commandFormula = formula;
        while ((commandFormula instanceof ExprUnary) && ((ExprUnary)commandFormula).op.equals(ExprUnary.Op.NOOP)) {
            commandFormula = ((ExprUnary) commandFormula).sub;
        }
        boolean isAnd = (commandFormula instanceof ExprBinary) && ((ExprBinary) commandFormula).op.equals(ExprBinary.Op.AND);
        if (!isAnd)
            throw new IllegalStateException("The command has facts but the main expression is not an AND expression");
        return ((ExprBinary)commandFormula).left;
    }

    public List<Command> getFactRelaxedCommands() {
        if (!hasFacts)
            throw new IllegalStateException("This command has no facts, use #hasFacts() to check before calling this method");
        Expr commandFormula = formula;
        while ((commandFormula instanceof ExprUnary) && ((ExprUnary)commandFormula).op.equals(ExprUnary.Op.NOOP)) {
            commandFormula = ((ExprUnary)commandFormula).sub;
        }
        boolean isAnd = (commandFormula instanceof ExprBinary) && ((ExprBinary) commandFormula).op.equals(ExprBinary.Op.AND);
        if (!isAnd)
            throw new IllegalStateException("The command has facts but the main expression is not an AND expression");
        Expr facts = ((ExprBinary)commandFormula).left;
        commandFormula = ((ExprBinary)commandFormula).right;
        List<Command> relaxedCommands = new LinkedList<>();
        if (facts instanceof ExprList) {
            ExprList factsAsList = (ExprList) facts;
            int factsCount = factsAsList.args.size();
            for (Expr relaxedFact : combine(factsAsList.args, factsCount)) {
                Expr relaxedFormula = ExprBinary.Op.AND.make(null, null, (Expr) relaxedFact.clone(), (Expr) commandFormula.clone());
                Command relaxedCommand = new Command(pos, nameExpr, label, check, overall, bitwidth, maxseq, expects, scope, additionalExactScopes, relaxedFormula, parent);
                relaxedCommands.add(relaxedCommand);
            }
        }
        Command noFactsCommand = new Command(pos, nameExpr, label, check, overall, bitwidth, maxseq, expects, scope, additionalExactScopes, (Expr) commandFormula.clone(), parent);
        relaxedCommands.add(noFactsCommand);
        return relaxedCommands;
    }

    private List<Expr> combine(List<Expr> facts, int max) {
        if (facts.isEmpty())
            return Collections.emptyList();
        List<Expr> allCombinations = new LinkedList<>();
        Expr fact = facts.get(0);
        List<Expr> constRest = facts.size() > 1?facts.subList(1, facts.size()):Collections.emptyList();
        List<Expr> rest = new LinkedList<>(constRest);
        if (!rest.isEmpty()) {
            List<Expr> newCombined = new LinkedList<>();
            List<Expr> combined = combine(rest, max - 1);
            for (Expr combination : combined) {
                ExprList combinedFact;
                if (combination instanceof ExprList && ((ExprList)combination).op.equals(ExprList.Op.AND)) {
                    ExprList combinationAsList = (ExprList) combination;
                    if (combinationAsList.args.size() + 1 >= max)
                        continue;
                    combinedFact = ((ExprList) combinationAsList.clone());
                    combinedFact.appendArg(fact);
                } else {
                    combinedFact = ExprList.makeAND(null, null, (Expr) fact.clone(), (Expr) combination.clone());
                }
                newCombined.add(combinedFact);
            }
            allCombinations.addAll(newCombined);
            allCombinations.addAll(combined);
        }
        allCombinations.add(fact);
        return allCombinations;
    }

    /**
     * Constructs a new Command object where it is the same as the current object,
     * except with a different formula.
     */
    public Command change(Expr newFormula) {
        return new Command(pos, nameExpr, label, check, overall, bitwidth, maxseq, expects, scope, additionalExactScopes, newFormula, parent);
    }

    /**
     * Constructs a new Command object where it is the same as the current object,
     * except with a different scope.
     */
    public Command change(ConstList<CommandScope> scope) {
        return new Command(pos, nameExpr, label, check, overall, bitwidth, maxseq, expects, scope, additionalExactScopes, formula, parent);
    }

    /**
     * Constructs a new Command object where it is the same as the current object,
     * except with a different list of "additional exact sigs".
     */
    public Command change(Sig... additionalExactScopes) {
        return new Command(pos, nameExpr, label, check, overall, bitwidth, maxseq, expects, scope, Util.asList(additionalExactScopes), formula, parent);
    }

    /**
     * Constructs a new Command object where it is the same as the current object,
     * except with a different scope for the given sig.
     */
    public Command change(Sig sig, boolean isExact, int newScope) throws ErrorSyntax {
        return change(sig, isExact, newScope, newScope, 1);
    }

    /**
     * Constructs a new Command object where it is the same as the current object,
     * except with a different scope for the given sig.
     */
    public Command change(Sig sig, boolean isExact, int startingScope, int endingScope, int increment) throws ErrorSyntax {
        for (int i = 0; i < scope.size(); i++)
            if (scope.get(i).sig == sig) {
                CommandScope sc = new CommandScope(scope.get(i).pos, sig, isExact, startingScope, endingScope, increment);
                return change(new TempList<CommandScope>(scope).set(i, sc).makeConst());
            }
        CommandScope sc = new CommandScope(Pos.UNKNOWN, sig, isExact, startingScope, endingScope, increment);
        return change(Util.append(scope, sc));
    }

    /**
     * Helper method that returns the scope corresponding to a given sig (or return
     * null if the sig isn't named in this command)
     */
    public CommandScope getScope(Sig sig) {
        for (int i = 0; i < scope.size(); i++)
            if (scope.get(i).sig == sig)
                return scope.get(i);
        return null;
    }

    /**
     * Helper method that returns true iff this command contains at least one
     * growable sig.
     */
    public ConstList<Sig> getGrowableSigs() {
        TempList<Sig> answer = new TempList<Sig>();
        for (CommandScope sc : scope)
            if (sc.startingScope != sc.endingScope)
                answer.add(sc.sig);
        return answer.makeConst();
    }

    /**
     * Return a modifiable copy of the set of all String constants used in this
     * command or in any facts embedded in this command.
     */
    public Set<String> getAllStringConstants(Iterable<Sig> sigs) throws Err {
        final Set<String> set = new HashSet<String>();
        final VisitQuery<Object> findString = new VisitQueryOnce<Object>() {

            @Override
            public final Object visit(ExprConstant x) throws Err {
                if (x.op == ExprConstant.Op.STRING)
                    set.add(x.string);
                return null;
            }

            @Override
            public Object visit(ExprCall x) throws Err {
                x.fun.getBody().accept(this);
                for (Expr e : x.args)
                    e.accept(this);
                return null;
            }
        };
        for (Command c = this; c != null; c = c.parent)
            c.formula.accept(findString);
        for (Sig s : sigs) {
            for (Expr e : s.getFacts())
                e.accept(findString);
            for (Decl d : s.getFieldDecls())
                d.expr.accept(findString);
        }
        return set;
    }

    /** {@inheritDoc} */
    @Override
    public final Pos pos() {
        return pos;
    }

    /** {@inheritDoc} */
    @Override
    public final Pos span() {
        return pos;
    }

    /** {@inheritDoc} */
    @Override
    public String getHTML() {
        return (check ? "<b>check</b> " : "<b>run</b> ") + label;
    }

    /** {@inheritDoc} */
    @Override
    public List< ? extends Browsable> getSubnodes() {
        return formula == null ? (new ArrayList<Browsable>(0)) : Util.asList(formula);
    }

    @Override
    protected void defineParentForComponents() {
        this.nameExpr.setBrowsableParent(this);
        //this.formula.setBrowsableParent(this);
    }

    @Override
    public Object clone() {
        Expr nameExprClone = (Expr) this.nameExpr.clone();
        List<Sig> sigsClone = new LinkedList<>();
        for (Sig s : this.additionalExactScopes) {
            sigsClone.add((Sig) s.clone());
        }
        Expr formulaClone = (Expr) this.formula.clone();
        Command parentClone = (Command) this.parent.clone();
        Command clone = new Command(this.pos, nameExprClone, this.label, this.check, this.overall, this.bitwidth, this.maxseq, this.expects, this.scope, ConstList.make(sigsClone), formulaClone, parentClone);
        clone.setID(getID());
        clone.setIDEnv(getIDEnv());
        if (isVariabilizationTest())
            clone.setAsVariabilizationTest();
        if (isPerfectOracleTest())
            clone.setAsPerfectOracleTest();
        return clone;
    }

}
