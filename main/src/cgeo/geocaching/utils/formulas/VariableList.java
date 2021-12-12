package cgeo.geocaching.utils.formulas;

import cgeo.geocaching.utils.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

/**
 * Encapsulates a {@link VariableMap} but adds semantic of a list to it
 * (e.g. it includes and maintains ordering).
 *
 * It also maintains modification state and provides hooks for loading and saving a Variable Lists state e.g. for persistence (load-from/store-to DB)
 */
public class VariableList {

    private static final char INVISIBLE_VAR_PREFIX = '_';

    private final VariableMap variableMap = new VariableMap();
    private final List<String> variableList = new ArrayList<>();
    private final Map<String, Long> variablesSet = new HashMap<>();

    private boolean wasModified = false;

    public static class VariableEntry {
        public final long id;
        public final String varname;
        public final String formula;

        public VariableEntry(final long id, final String varname, final String formula) {
            this.id = id;
            this.varname = varname;
            this.formula = formula;
        }
    }

    @Nullable
    public Value getValue(final String var) {
        final VariableMap.VariableState state = getState(var);
        return state == null ? null : state.getResult();
    }

    public boolean contains(final String var) {
        return variablesSet.containsKey(var);
    }

    @Nullable
    public VariableMap.VariableState getState(final String var) {
        return variableMap.get(var);
    }

    public int size() {
        return variableList.size();
    }

    public boolean isEmpty() {
        return size() == 0;
    }

    public List<String> asList() {
        return variableList;
    }

    public Set<String> asSet() {
        return variablesSet.keySet();
    }

    public void clear() {
        if (variableList.isEmpty()) {
            return;
        }
        variableMap.clear();
        variableList.clear();
        variablesSet.clear();
        wasModified = true;
    }

    /**
     * puts a new variable into the cache. If same var already exists it is overridden.
     * If varname given is 'null', a nonvisible var name (starting with NONVISIBLE_PREFIX) is created and returned
     */
    @NonNull
    public String addVariable(@Nullable  final String var, @Nullable final String formula, final int ppos) {
        int pos = Math.min(variableList.size(), Math.max(0, ppos));
        final String varname = var == null ? variableMap.createNonContainedKey("" + INVISIBLE_VAR_PREFIX) : var;
        if (variablesSet.containsKey(varname)) {
            final int removeIdx = removeVariable(varname);

            if (removeIdx >= 0 && removeIdx < pos) {
                pos--;
            }
        }
        variableMap.put(varname, formula);
        variableList.add(pos, varname);
        variablesSet.put(varname, null);
        wasModified = true;
        return varname;
    }

    /** returns true if there actually was a change (var is contained and formula is different), false otherwise */
    public boolean changeVariable(final String var, final String formula) {
        if (!variablesSet.containsKey(var) ||
            Objects.equals(Objects.requireNonNull(variableMap.get(var)).getFormulaString(), formula)) {
            return false;
        }
        variableMap.put(var, formula);
        wasModified = true;
        return true;
    }

    public static boolean isVisible(final String varname) {
        return !StringUtils.isBlank(varname) && varname.charAt(0) != INVISIBLE_VAR_PREFIX;
    }

    /** returns the list position where variable was removed, or -1 if var was not found */
    public int removeVariable(@NonNull final String var) {
        if (!variablesSet.containsKey(var)) {
            return -1;
        }
        final int idx = variableList.indexOf(var);
        variableList.remove(idx);
        variableMap.remove(var);
        variablesSet.remove(var);
        wasModified = true;
        return idx;
    }

    public void sortVariables(final Comparator<String> comp) {
        Collections.sort(variableList, comp);
        wasModified = true;
    }


    /** gets an alphabetically sorted list of all vars missing in any var for calculation */
    @NonNull
    public List<String> getAllMissingVars() {
        final List<String> varsMissing = new ArrayList<>(variableMap.getVars());
        varsMissing.removeAll(variableList);
        TextUtils.sortListLocaleAware(varsMissing);
        return varsMissing;
    }

    public void setEntries(final List<VariableEntry> vars) {
        clear();
        for (VariableEntry entry : vars) {
            if (this.variablesSet.containsKey(entry.varname)) {
                //must be a database error, ignore duplicate variable
                continue;
            }
            variableMap.put(entry.varname, entry.formula);
            this.variableList.add(entry.varname);
            this.variablesSet.put(entry.varname, entry.id);
        }
        wasModified = false;
    }

    public boolean wasModified() {
        return wasModified;
    }

    public void resetModified() {
        wasModified = false;
    }

    public List<VariableEntry> getEntries() {
        final List<VariableEntry> rows = new ArrayList<>();
        for (String v : this.variableList) {
            rows.add(new VariableEntry(
                this.variablesSet.get(v) == null ? -1 : Objects.requireNonNull(this.variablesSet.get(v)),
                v, Objects.requireNonNull(this.variableMap.get(v)).getFormulaString()));
        }
        return rows;
    }

    @Nullable
    public Character getLowestMissingChar() {
        if (!asSet().contains("A")) {
            return 'A';
        }
        int lowestContChar = 'A';
        while (lowestContChar < 'Z' && asSet().contains("" + (char) (lowestContChar + 1))) {
            lowestContChar++;
        }
        return lowestContChar == 'Z' ? null : (char) (lowestContChar + 1);
    }

}
