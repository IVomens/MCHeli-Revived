package com.ivomens.mcheli.core.hud;

import com.ivomens.mcheli.core.hud.expr.Formula;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The meaning of formulas that HUD scripts rely on, and what the compiler reports when a HUD loads. */
class FormulaTest {

    private final HudFrame frame = new HudFrame();
    private final List<String> problems = new ArrayList<>();

    private double evaluate(String source) {
        return Formula.compile(source, HudVariable.TABLE, this.problems::add).evaluate(this.frame);
    }

    @Test
    void integersStayIntegersUntilADoubleJoinsIn() {
        this.frame.set(HudVariable.HP, 37);
        this.frame.set(HudVariable.MAX_HP, 120);
        this.frame.set(HudVariable.PITCH, 45.0);
        assertEquals(3.0, evaluate("7/2"));
        assertEquals(3.5, evaluate("7/2.0"));
        assertEquals(0.0, evaluate("hp/max_hp"));
        assertEquals(84.0, evaluate("64+(40*(pitch/90))"));
        assertEquals(-1.0, evaluate("-7%3"));
        assertEquals(Long.MIN_VALUE, evaluate("9223372036854775807+1"));
        assertThrows(ArithmeticException.class, () -> evaluate("hp/0"));
        assertEquals(Double.POSITIVE_INFINITY, evaluate("hp/0.0"));
    }

    @Test
    void coloursAreHexIntegers() {
        assertEquals(4280865864.0, evaluate("0xff28d448"));
        assertEquals((double) 0xFF0080FFL, evaluate("((255)<<24)|((0)<<16)|((128)<<8 )|((255)<<0 )"));
        assertEquals(4294967295.0, evaluate(Formula.normalise("#FFFFFFFF")));
    }

    @Test
    void booleansCompareWithBooleansOnly() {
        this.frame.set(HudVariable.HOVERING, true);
        assertEquals(0.0, evaluate("hovering==1"), "a boolean is never equal to a number");
        assertEquals(1.0, evaluate("hovering!=1"));
        assertEquals(1.0, evaluate("hovering==true"));
        assertThrows(IllegalStateException.class, () -> evaluate("hovering>0"));
    }

    @Test
    void legacyIntegerVariablesAreTestedAsBooleans() {
        this.frame.set(HudVariable.RELOADING, false);
        assertEquals(1.0, evaluate("reloading==0"));
        assertEquals(0.0, evaluate("reloading!=0"));
        assertEquals(0.0, evaluate("reloading==1"));
        assertEquals(1.0, evaluate("reloading!=1"));
        assertEquals(1.0, evaluate("is_uav==0||reloading"));
        assertTrue(this.problems.isEmpty(), this.problems::toString);
    }

    @Test
    void doublesCompareLikeDoubleCompare() {
        this.frame.set(HudVariable.ROLL, 0.0);
        assertEquals(0.0, evaluate("-roll==0"), "-0.0 is below 0.0");
        assertEquals(1.0, evaluate("-roll<roll"));
        assertEquals(1.0, evaluate("(roll/roll)==(roll/roll)"), "NaN equals itself");
    }

    @Test
    void conditionalsMayMixTypes() {
        this.frame.set(HudVariable.IS_UAV, true);
        this.frame.set(HudVariable.HP, 7);
        this.frame.set(HudVariable.LOCK, 2.5);
        assertEquals(3.0, evaluate("(is_uav?hp:lock)/2"), "the integer branch divides as an integer");
        assertEquals(1.25, evaluate("(!is_uav?hp:lock)/2"));
        assertEquals(7.0, evaluate("max(lock,hp)"));
        assertEquals(3.0, evaluate("math.round(lock)"));
        assertEquals(7.0, evaluate("math.abs(-hp)"));
    }

    @Test
    void anUnknownNameIsReportedAndHasNoValue() {
        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> evaluate("no_such_variable"));
        assertEquals("HUD expression returned null: no_such_variable", failure.getMessage());
        assertEquals(List.of("'no_such_variable': 'no_such_variable' is not a HUD variable, so it has no value"),
                this.problems);
        assertEquals(0.0, evaluate("no_such_variable==1"), "nil equals nothing but nil, as in Aviator");
    }

    @Test
    void whatCannotWorkIsReportedAtLoadAndFailsOnlyWhenReached() {
        this.frame.set(HudVariable.IS_UAV, false);
        assertEquals(2.0, evaluate("is_uav?(is_uav+1):2"));
        assertEquals(List.of("'is_uav?(is_uav+1):2': cannot add a boolean and an integer"), this.problems);
        assertEquals(0.0, evaluate("is_uav&&hp"), "the right side is not reached");
        assertThrows(IllegalStateException.class, () -> evaluate("math.sin(is_uav)"));
    }

    @Test
    void textThatIsNoFormulaIsReported() {
        for (String source : List.of("", "((1", "1)", "1 2", "a=1", "'text'", "1e+2", "0x")) {
            this.problems.clear();
            Formula formula = Formula.compile(source, HudVariable.TABLE, this.problems::add);
            assertFalse(formula.readable(), source);
            assertEquals(1, this.problems.size(), source);
            assertThrows(IllegalStateException.class, () -> formula.evaluate(this.frame), source);
        }
    }

    @Test
    void formulasAreNormalisedLikeHudFiles() {
        assertEquals("altitude>10?0xcfffffff:0x0", Formula.normalise(" Altitude > 10 ? #CFFFFFFF : #0"));
        assertEquals("5", Formula.normalise("+5"));
        assertEquals("0", Formula.normalise(null));
    }
}
