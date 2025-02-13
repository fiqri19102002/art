/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import java.lang.reflect.Array;
import java.lang.reflect.Method;

/**
 * Tests for SIMD related optimizations.
 */
public class Main {

  /// CHECK-START: void Main.unroll(float[], float[]) loop_optimization (before)
  /// CHECK-DAG: <<Cons:f\d+>> FloatConstant 2.5                   loop:none
  /// CHECK-DAG: <<Phi:i\d+>>  Phi                                 loop:<<Loop:B\d+>> outer_loop:none
  /// CHECK-DAG: <<Get:f\d+>>  ArrayGet                            loop:<<Loop>>      outer_loop:none
  /// CHECK-DAG: <<Mul:f\d+>>  Mul [<<Get>>,<<Cons>>]              loop:<<Loop>>      outer_loop:none
  /// CHECK-DAG:               ArraySet [{{l\d+}},<<Phi>>,<<Mul>>] loop:<<Loop>>      outer_loop:none
  //
  /// CHECK-START-{X86_64,ARM64}: void Main.unroll(float[], float[]) loop_optimization (after)
  /// CHECK-DAG: <<Cons:f\d+>> FloatConstant 2.5                    loop:none

  /// CHECK-IF:     hasIsaFeature("sve") and os.environ.get('ART_FORCE_TRY_PREDICATED_SIMD') == 'true'
  //
  ///     CHECK-DAG: <<Repl:d\d+>>  VecReplicateScalar [<<Cons>>,{{j\d+}}]         loop:none
  ///     CHECK-NOT:                VecReplicateScalar
  ///     CHECK-DAG: <<Phi:i\d+>>   Phi                                            loop:<<Loop:B\d+>> outer_loop:none
  ///     CHECK-DAG: <<LoopP:j\d+>> VecPredWhile [<<Phi>>,{{i\d+}}]                loop:<<Loop>>      outer_loop:none
  ///     CHECK-DAG: <<Get1:d\d+>>  VecLoad [{{l\d+}},<<Phi>>,<<LoopP>>]           loop:<<Loop>>      outer_loop:none
  ///     CHECK-DAG: <<Mul1:d\d+>>  VecMul [<<Get1>>,<<Repl>>,<<LoopP>>]           loop:<<Loop>>      outer_loop:none
  ///     CHECK-DAG:                VecStore [{{l\d+}},<<Phi>>,<<Mul1>>,<<LoopP>>] loop:<<Loop>>      outer_loop:none
  ///     CHECK-DAG: <<Add:i\d+>>   Add [<<Phi>>,{{i\d+}}]                         loop:<<Loop>>      outer_loop:none
  //      No unroll for SVE yet.
  //
  /// CHECK-ELSE:
  //
  ///     CHECK-DAG: <<Incr:i\d+>>  IntConstant 4                        loop:none
  ///     CHECK-DAG: <<Repl:d\d+>>  VecReplicateScalar [<<Cons>>]        loop:none
  ///     CHECK-NOT:                VecReplicateScalar
  ///     CHECK-DAG: <<Phi:i\d+>>   Phi                                  loop:<<Loop:B\d+>> outer_loop:none
  ///     CHECK-DAG: <<Get1:d\d+>>  VecLoad [{{l\d+}},<<Phi>>]           loop:<<Loop>>      outer_loop:none
  ///     CHECK-DAG: <<Mul1:d\d+>>  VecMul [<<Get1>>,<<Repl>>]           loop:<<Loop>>      outer_loop:none
  ///     CHECK-DAG:                VecStore [{{l\d+}},<<Phi>>,<<Mul1>>] loop:<<Loop>>      outer_loop:none
  ///     CHECK-DAG: <<Add:i\d+>>   Add [<<Phi>>,<<Incr>>]               loop:<<Loop>>      outer_loop:none
  ///     CHECK-DAG: <<Get2:d\d+>>  VecLoad [{{l\d+}},<<Add>>]           loop:<<Loop>>      outer_loop:none
  ///     CHECK-DAG: <<Mul2:d\d+>>  VecMul [<<Get2>>,<<Repl>>]           loop:<<Loop>>      outer_loop:none
  ///     CHECK-DAG:                VecStore [{{l\d+}},<<Add>>,<<Mul2>>] loop:<<Loop>>      outer_loop:none
  ///     CHECK-DAG:                Add [<<Add>>,<<Incr>>]               loop:<<Loop>>      outer_loop:none
  //
  /// CHECK-FI:
  private static void unroll(float[] x, float[] y) {
    for (int i = 0; i < 100; i++) {
      x[i] = y[i] * 2.5f;
    }
  }

  /// CHECK-START-{X86_64,ARM64}: void Main.stencil(int[], int[], int) loop_optimization (after)
  /// CHECK-DAG: <<CP1:i\d+>>   IntConstant 1                         loop:none
  /// CHECK-DAG: <<CP2:i\d+>>   IntConstant 2                         loop:none
  /// CHECK-IF:     hasIsaFeature("sve") and os.environ.get('ART_FORCE_TRY_PREDICATED_SIMD') == 'true'
  //
  ///     CHECK-DAG: <<Phi:i\d+>>   Phi                                             loop:<<Loop:B\d+>> outer_loop:none
  ///     CHECK-DAG: <<LoopP:j\d+>> VecPredWhile [<<Phi>>,{{i\d+}}]                 loop:<<Loop>>      outer_loop:none
  ///     CHECK-DAG: <<Add1:i\d+>>  Add [<<Phi>>,<<CP1>>]                           loop:<<Loop>>      outer_loop:none
  ///     CHECK-DAG: <<Get1:d\d+>>  VecLoad [{{l\d+}},<<Phi>>,<<LoopP>>]            loop:<<Loop>>      outer_loop:none
  ///     CHECK-DAG: <<Get2:d\d+>>  VecLoad [{{l\d+}},<<Add1>>,<<LoopP>>]           loop:<<Loop>>      outer_loop:none
  ///     CHECK-DAG: <<Add2:d\d+>>  VecAdd [<<Get1>>,<<Get2>>,<<LoopP>>]            loop:<<Loop>>      outer_loop:none
  ///     CHECK-DAG: <<Add3:i\d+>>  Add [<<Phi>>,<<CP2>>]                           loop:<<Loop>>      outer_loop:none
  ///     CHECK-DAG: <<Get3:d\d+>>  VecLoad [{{l\d+}},<<Add3>>,<<LoopP>>]           loop:<<Loop>>      outer_loop:none
  ///     CHECK-DAG: <<Add4:d\d+>>  VecAdd [<<Add2>>,<<Get3>>,<<LoopP>>]            loop:<<Loop>>      outer_loop:none
  ///     CHECK-DAG:                VecStore [{{l\d+}},<<Add1>>,<<Add4>>,<<LoopP>>] loop:<<Loop>>      outer_loop:none
  //
  /// CHECK-ELSE:
  //
  ///     CHECK-DAG: <<Phi:i\d+>>   Phi                                   loop:<<Loop:B\d+>> outer_loop:none
  ///     CHECK-DAG: <<Add1:i\d+>>  Add [<<Phi>>,<<CP1>>]                 loop:<<Loop>>      outer_loop:none
  ///     CHECK-DAG: <<Get1:d\d+>>  VecLoad [{{l\d+}},<<Phi>>]            loop:<<Loop>>      outer_loop:none
  ///     CHECK-DAG: <<Get2:d\d+>>  VecLoad [{{l\d+}},<<Add1>>]           loop:<<Loop>>      outer_loop:none
  ///     CHECK-DAG: <<Add2:d\d+>>  VecAdd [<<Get1>>,<<Get2>>]            loop:<<Loop>>      outer_loop:none
  ///     CHECK-DAG: <<Add3:i\d+>>  Add [<<Phi>>,<<CP2>>]                 loop:<<Loop>>      outer_loop:none
  ///     CHECK-DAG: <<Get3:d\d+>>  VecLoad [{{l\d+}},<<Add3>>]           loop:<<Loop>>      outer_loop:none
  ///     CHECK-DAG: <<Add4:d\d+>>  VecAdd [<<Add2>>,<<Get3>>]            loop:<<Loop>>      outer_loop:none
  ///     CHECK-DAG:                VecStore [{{l\d+}},<<Add1>>,<<Add4>>] loop:<<Loop>>      outer_loop:none
  //
  /// CHECK-FI:
  private static void stencil(int[] a, int[] b, int n) {
    for (int i = 1; i < n - 1; i++) {
      a[i] = b[i - 1] + b[i] + b[i + 1];
    }
  }

  // Array size is chosen to be such a constant, that the loop trip count (in the test below)
  // is a multiple of vector length and unroll factor; hence clean up is needed exclusively for
  // the array references test.
  public static final int STENCIL_ARRAY_SIZE = 130;

  /// CHECK-START-{X86_64,ARM64}: void Main.$noinline$stencilConstSize(int[], int[]) loop_optimization (after)
  /// CHECK-DAG: <<C0:i\d+>>    IntConstant 0
  /// CHECK-DAG: <<CP1:i\d+>>   IntConstant 1
  /// CHECK-DAG: <<CP2:i\d+>>   IntConstant 2
  /// CHECK-DAG: <<Arr0:l\d+>>  ParameterValue
  /// CHECK-DAG: <<Arr1:l\d+>>  ParameterValue
  /// CHECK-DAG: <<ArrCh:z\d+>> NotEqual [<<Arr0>>,<<Arr1>>]         loop:none
  /// CHECK-DAG: <<TCSel:i\d+>> Select [<<C0>>,{{i\d+}},<<ArrCh>>]   loop:none
  /// CHECK-DAG: <<PhiV:i\d+>>  Phi [<<C0>>,{{i\d+}}]                loop:<<LoopV:B\d+>> outer_loop:none
  //
  /// CHECK-IF:     hasIsaFeature("sve") and os.environ.get('ART_FORCE_TRY_PREDICATED_SIMD') == 'true'
  //
  ///     CHECK-DAG: <<LoopP:j\d+>> VecPredWhile [<<PhiV>>,{{i\d+}}]                loop:<<LoopV>>      outer_loop:none
  ///     CHECK-DAG: <<Add1:i\d+>>  Add [<<PhiV>>,<<CP1>>]                          loop:<<LoopV>>      outer_loop:none
  ///     CHECK-DAG: <<Get1:d\d+>>  VecLoad [{{l\d+}},<<PhiV>>,<<LoopP>>]           loop:<<LoopV>>      outer_loop:none
  ///     CHECK-DAG: <<Get2:d\d+>>  VecLoad [{{l\d+}},<<Add1>>,<<LoopP>>]           loop:<<LoopV>>      outer_loop:none
  ///     CHECK-DAG: <<Add2:d\d+>>  VecAdd [<<Get1>>,<<Get2>>,<<LoopP>>]            loop:<<LoopV>>      outer_loop:none
  ///     CHECK-DAG: <<Add3:i\d+>>  Add [<<PhiV>>,<<CP2>>]                          loop:<<LoopV>>      outer_loop:none
  ///     CHECK-DAG: <<Get3:d\d+>>  VecLoad [{{l\d+}},<<Add3>>,<<LoopP>>]           loop:<<LoopV>>      outer_loop:none
  ///     CHECK-DAG: <<Add4:d\d+>>  VecAdd [<<Add2>>,<<Get3>>,<<LoopP>>]            loop:<<LoopV>>      outer_loop:none
  ///     CHECK-DAG:                VecStore [{{l\d+}},<<Add1>>,<<Add4>>,<<LoopP>>] loop:<<LoopV>>      outer_loop:none
  //
  /// CHECK-ELSE:
  //
  ///     CHECK-DAG: <<Add1:i\d+>>  Add [<<PhiV>>,<<CP1>>]                          loop:<<LoopV>>      outer_loop:none
  ///     CHECK-DAG: <<Get1:d\d+>>  VecLoad [{{l\d+}},<<PhiV>>]                     loop:<<LoopV>>      outer_loop:none
  ///     CHECK-DAG: <<Get2:d\d+>>  VecLoad [{{l\d+}},<<Add1>>]                     loop:<<LoopV>>      outer_loop:none
  ///     CHECK-DAG: <<Add2:d\d+>>  VecAdd [<<Get1>>,<<Get2>>]                      loop:<<LoopV>>      outer_loop:none
  ///     CHECK-DAG: <<Add3:i\d+>>  Add [<<PhiV>>,<<CP2>>]                          loop:<<LoopV>>      outer_loop:none
  ///     CHECK-DAG: <<Get3:d\d+>>  VecLoad [{{l\d+}},<<Add3>>]                     loop:<<LoopV>>      outer_loop:none
  ///     CHECK-DAG: <<Add4:d\d+>>  VecAdd [<<Add2>>,<<Get3>>]                      loop:<<LoopV>>      outer_loop:none
  ///     CHECK-DAG:                VecStore [{{l\d+}},<<Add1>>,<<Add4>>]           loop:<<LoopV>>      outer_loop:none
  //
  /// CHECK-FI:
  //
  // Cleanup loop.
  //
  /// CHECK-DAG: <<PhiS:i\d+>>   Phi [<<PhiV>>,{{i\d+}}]             loop:<<LoopS:B\d+>> outer_loop:none
  /// CHECK-DAG:                 ArrayGet                            loop:<<LoopS>>      outer_loop:none
  /// CHECK-DAG:                 ArrayGet                            loop:<<LoopS>>      outer_loop:none
  /// CHECK-DAG:                 ArrayGet                            loop:<<LoopS>>      outer_loop:none
  /// CHECK-DAG:                 ArraySet                            loop:<<LoopS>>      outer_loop:none
  //
  // Checks the disambiguation runtime test for array references.
  //
  private static void $noinline$stencilConstSize(int[] a, int[] b) {
    for (int i = 1; i < STENCIL_ARRAY_SIZE - 1; i++) {
      a[i] = b[i - 1] + b[i] + b[i + 1];
    }
  }

  /// CHECK-START: void Main.stencilAddInt(int[], int[], int) loop_optimization (before)
  /// CHECK-DAG: <<CP1:i\d+>>   IntConstant 1                        loop:none
  /// CHECK-DAG: <<CM1:i\d+>>   IntConstant -1                       loop:none
  /// CHECK-DAG: <<Phi:i\d+>>   Phi                                  loop:<<Loop:B\d+>> outer_loop:none
  /// CHECK-DAG: <<Add1:i\d+>>  Add [<<Phi>>,<<CM1>>]                loop:<<Loop>>      outer_loop:none
  /// CHECK-DAG: <<Get1:i\d+>>  ArrayGet [{{l\d+}},<<Add1>>]         loop:<<Loop>>      outer_loop:none
  /// CHECK-DAG: <<Get2:i\d+>>  ArrayGet [{{l\d+}},<<Phi>>]          loop:<<Loop>>      outer_loop:none
  /// CHECK-DAG: <<Add2:i\d+>>  Add [<<Get1>>,<<Get2>>]              loop:<<Loop>>      outer_loop:none
  /// CHECK-DAG: <<Add3:i\d+>>  Add [<<Phi>>,<<CP1>>]                loop:<<Loop>>      outer_loop:none
  /// CHECK-DAG: <<Get3:i\d+>>  ArrayGet [{{l\d+}},<<Add3>>]         loop:<<Loop>>      outer_loop:none
  /// CHECK-DAG: <<Add4:i\d+>>  Add [<<Add2>>,<<Get3>>]              loop:<<Loop>>      outer_loop:none
  /// CHECK-DAG:                ArraySet [{{l\d+}},<<Phi>>,<<Add4>>] loop:<<Loop>>      outer_loop:none

  /// CHECK-START-{X86_64,ARM64}: void Main.stencilAddInt(int[], int[], int) loop_optimization (after)
  /// CHECK-DAG: <<CP1:i\d+>>   IntConstant 1                         loop:none
  /// CHECK-DAG: <<CP2:i\d+>>   IntConstant 2                         loop:none
  /// CHECK-IF:     hasIsaFeature("sve") and os.environ.get('ART_FORCE_TRY_PREDICATED_SIMD') == 'true'
  //
  ///     CHECK-DAG: <<Phi:i\d+>>   Phi                                             loop:<<Loop:B\d+>> outer_loop:none
  ///     CHECK-DAG: <<LoopP:j\d+>> VecPredWhile [<<Phi>>,{{i\d+}}]                 loop:<<Loop>>      outer_loop:none
  ///     CHECK-DAG: <<Add1:i\d+>>  Add [<<Phi>>,<<CP1>>]                           loop:<<Loop>>      outer_loop:none
  ///     CHECK-DAG: <<Get1:d\d+>>  VecLoad [{{l\d+}},<<Phi>>,<<LoopP>>]            loop:<<Loop>>      outer_loop:none
  ///     CHECK-DAG: <<Get2:d\d+>>  VecLoad [{{l\d+}},<<Add1>>,<<LoopP>>]           loop:<<Loop>>      outer_loop:none
  ///     CHECK-DAG: <<Add2:d\d+>>  VecAdd [<<Get1>>,<<Get2>>,<<LoopP>>]            loop:<<Loop>>      outer_loop:none
  ///     CHECK-DAG: <<Add3:i\d+>>  Add [<<Phi>>,<<CP2>>]                           loop:<<Loop>>      outer_loop:none
  ///     CHECK-DAG: <<Get3:d\d+>>  VecLoad [{{l\d+}},<<Add3>>,<<LoopP>>]           loop:<<Loop>>      outer_loop:none
  ///     CHECK-DAG: <<Add4:d\d+>>  VecAdd [<<Add2>>,<<Get3>>,<<LoopP>>]            loop:<<Loop>>      outer_loop:none
  ///     CHECK-DAG:                VecStore [{{l\d+}},<<Add1>>,<<Add4>>,<<LoopP>>] loop:<<Loop>>      outer_loop:none
  //
  /// CHECK-ELSE:
  //
  ///     CHECK-DAG: <<Phi:i\d+>>   Phi                                   loop:<<Loop:B\d+>> outer_loop:none
  ///     CHECK-DAG: <<Add1:i\d+>>  Add [<<Phi>>,<<CP1>>]                 loop:<<Loop>>      outer_loop:none
  ///     CHECK-DAG: <<Get1:d\d+>>  VecLoad [{{l\d+}},<<Phi>>]            loop:<<Loop>>      outer_loop:none
  ///     CHECK-DAG: <<Get2:d\d+>>  VecLoad [{{l\d+}},<<Add1>>]           loop:<<Loop>>      outer_loop:none
  ///     CHECK-DAG: <<Add2:d\d+>>  VecAdd [<<Get1>>,<<Get2>>]            loop:<<Loop>>      outer_loop:none
  ///     CHECK-DAG: <<Add3:i\d+>>  Add [<<Phi>>,<<CP2>>]                 loop:<<Loop>>      outer_loop:none
  ///     CHECK-DAG: <<Get3:d\d+>>  VecLoad [{{l\d+}},<<Add3>>]           loop:<<Loop>>      outer_loop:none
  ///     CHECK-DAG: <<Add4:d\d+>>  VecAdd [<<Add2>>,<<Get3>>]            loop:<<Loop>>      outer_loop:none
  ///     CHECK-DAG:                VecStore [{{l\d+}},<<Add1>>,<<Add4>>] loop:<<Loop>>      outer_loop:none
  //
  /// CHECK-FI:
  private static void stencilAddInt(int[] a, int[] b, int n) {
    int minus1 = $inline$constMinus1();
    for (int i = 1; i < n + minus1; i++) {
      a[i] = b[i + minus1] + b[i] + b[i + 1];
    }
  }

  private static int $inline$constMinus1() {
    return -1;
  }

  /// CHECK-START: void Main.stencilSubInt(int[], int[], int) loop_optimization (before)
  /// CHECK-DAG: <<PAR3:i\d+>>  ParameterValue                       loop:none
  /// CHECK-DAG: <<CP1:i\d+>>   IntConstant 1                        loop:none
  /// CHECK-DAG: <<Sub1:i\d+>>  Sub [<<PAR3>>,<<CP1>>]               loop:none
  /// CHECK-DAG: <<Phi:i\d+>>   Phi                                  loop:<<Loop:B\d+>> outer_loop:none
  /// CHECK-DAG: <<Sub2:i\d+>>  Sub [<<Phi>>,<<CP1>>]                loop:<<Loop>>      outer_loop:none
  /// CHECK-DAG: <<Get1:i\d+>>  ArrayGet [{{l\d+}},<<Sub2>>]         loop:<<Loop>>      outer_loop:none
  /// CHECK-DAG: <<Get2:i\d+>>  ArrayGet [{{l\d+}},<<Phi>>]          loop:<<Loop>>      outer_loop:none
  /// CHECK-DAG: <<Add1:i\d+>>  Add [<<Get1>>,<<Get2>>]              loop:<<Loop>>      outer_loop:none
  /// CHECK-DAG: <<Add2:i\d+>>  Add [<<Phi>>,<<CP1>>]                loop:<<Loop>>      outer_loop:none
  /// CHECK-DAG: <<Get3:i\d+>>  ArrayGet [{{l\d+}},<<Add2>>]         loop:<<Loop>>      outer_loop:none
  /// CHECK-DAG: <<Add3:i\d+>>  Add [<<Add1>>,<<Get3>>]              loop:<<Loop>>      outer_loop:none
  /// CHECK-DAG:                ArraySet [{{l\d+}},<<Phi>>,<<Add3>>] loop:<<Loop>>      outer_loop:none

  /// CHECK-START-{X86_64,ARM64}: void Main.stencilSubInt(int[], int[], int) loop_optimization (after)
  /// CHECK-DAG: <<CP1:i\d+>>   IntConstant 1                         loop:none
  /// CHECK-DAG: <<CP2:i\d+>>   IntConstant 2                         loop:none
  /// CHECK-IF:     hasIsaFeature("sve") and os.environ.get('ART_FORCE_TRY_PREDICATED_SIMD') == 'true'
  //
  ///     CHECK-DAG: <<Phi:i\d+>>   Phi                                             loop:<<Loop:B\d+>> outer_loop:none
  ///     CHECK-DAG: <<LoopP:j\d+>> VecPredWhile [<<Phi>>,{{i\d+}}]                 loop:<<Loop>>      outer_loop:none
  ///     CHECK-DAG: <<Add1:i\d+>>  Add [<<Phi>>,<<CP1>>]                           loop:<<Loop>>      outer_loop:none
  ///     CHECK-DAG: <<Get1:d\d+>>  VecLoad [{{l\d+}},<<Phi>>,<<LoopP>>]            loop:<<Loop>>      outer_loop:none
  ///     CHECK-DAG: <<Get2:d\d+>>  VecLoad [{{l\d+}},<<Add1>>,<<LoopP>>]           loop:<<Loop>>      outer_loop:none
  ///     CHECK-DAG: <<Add2:d\d+>>  VecAdd [<<Get1>>,<<Get2>>,<<LoopP>>]            loop:<<Loop>>      outer_loop:none
  ///     CHECK-DAG: <<Add3:i\d+>>  Add [<<Phi>>,<<CP2>>]                           loop:<<Loop>>      outer_loop:none
  ///     CHECK-DAG: <<Get3:d\d+>>  VecLoad [{{l\d+}},<<Add3>>,<<LoopP>>]           loop:<<Loop>>      outer_loop:none
  ///     CHECK-DAG: <<Add4:d\d+>>  VecAdd [<<Add2>>,<<Get3>>,<<LoopP>>]            loop:<<Loop>>      outer_loop:none
  ///     CHECK-DAG:                VecStore [{{l\d+}},<<Add1>>,<<Add4>>,<<LoopP>>] loop:<<Loop>>      outer_loop:none
  //
  /// CHECK-ELSE:
  //
  ///     CHECK-DAG: <<Phi:i\d+>>   Phi                                   loop:<<Loop:B\d+>> outer_loop:none
  ///     CHECK-DAG: <<Add1:i\d+>>  Add [<<Phi>>,<<CP1>>]                 loop:<<Loop>>      outer_loop:none
  ///     CHECK-DAG: <<Get1:d\d+>>  VecLoad [{{l\d+}},<<Phi>>]            loop:<<Loop>>      outer_loop:none
  ///     CHECK-DAG: <<Get2:d\d+>>  VecLoad [{{l\d+}},<<Add1>>]           loop:<<Loop>>      outer_loop:none
  ///     CHECK-DAG: <<Add2:d\d+>>  VecAdd [<<Get1>>,<<Get2>>]            loop:<<Loop>>      outer_loop:none
  ///     CHECK-DAG: <<Add3:i\d+>>  Add [<<Phi>>,<<CP2>>]                 loop:<<Loop>>      outer_loop:none
  ///     CHECK-DAG: <<Get3:d\d+>>  VecLoad [{{l\d+}},<<Add3>>]           loop:<<Loop>>      outer_loop:none
  ///     CHECK-DAG: <<Add4:d\d+>>  VecAdd [<<Add2>>,<<Get3>>]            loop:<<Loop>>      outer_loop:none
  ///     CHECK-DAG:                VecStore [{{l\d+}},<<Add1>>,<<Add4>>] loop:<<Loop>>      outer_loop:none
  //
  /// CHECK-FI:
  private static void stencilSubInt(int[] a, int[] b, int n) {
    int plus1 = $inline$constPlus1();
    for (int i = 1; i < n - plus1; i++) {
      a[i] = b[i - plus1] + b[i] + b[i + 1];
    }
  }

  private static int $inline$constPlus1() {
    return 1;
  }

  /// CHECK-START: long Main.longInductionReduction(long[]) loop_optimization (before)
  /// CHECK-DAG: <<L0:j\d+>>    LongConstant 0             loop:none
  /// CHECK-DAG: <<L1:j\d+>>    LongConstant 1             loop:none
  /// CHECK-DAG: <<I0:i\d+>>    IntConstant 0              loop:none
  /// CHECK-DAG: <<Get:j\d+>>   ArrayGet [{{l\d+}},<<I0>>] loop:none
  /// CHECK-DAG: <<Phi1:j\d+>>  Phi [<<L0>>,<<Add1:j\d+>>] loop:<<Loop:B\d+>> outer_loop:none
  /// CHECK-DAG: <<Phi2:j\d+>>  Phi [<<L1>>,<<Add2:j\d+>>] loop:<<Loop>>      outer_loop:none
  /// CHECK-DAG: <<Add2>>       Add [<<Phi2>>,<<Get>>]     loop:<<Loop>>      outer_loop:none
  /// CHECK-DAG: <<Add1>>       Add [<<Phi1>>,<<L1>>]      loop:<<Loop>>      outer_loop:none
  //
  /// CHECK-START-{X86_64,ARM64}: long Main.longInductionReduction(long[]) loop_optimization (after)
  /// CHECK-DAG: <<L0:j\d+>>    LongConstant 0               loop:none
  /// CHECK-DAG: <<L1:j\d+>>    LongConstant 1               loop:none
  /// CHECK-DAG: <<I0:i\d+>>    IntConstant 0                loop:none
  /// CHECK-DAG: <<Get:j\d+>>   ArrayGet [{{l\d+}},<<I0>>]   loop:none
  /// CHECK-IF:     hasIsaFeature("sve") and os.environ.get('ART_FORCE_TRY_PREDICATED_SIMD') == 'true'
  //
  ///     CHECK-DAG: <<Rep:d\d+>>   VecReplicateScalar [<<Get>>,{{j\d+}}]  loop:none
  ///     CHECK-DAG: <<Set:d\d+>>   VecSetScalars [<<L1>>,{{j\d+}}]        loop:none
  ///     CHECK-DAG: <<Phi1:j\d+>>  Phi [<<L0>>,{{j\d+}}]                  loop:<<Loop:B\d+>> outer_loop:none
  ///     CHECK-DAG: <<Phi2:d\d+>>  Phi [<<Set>>,{{d\d+}}]                 loop:<<Loop>>      outer_loop:none
  ///     CHECK-DAG: <<LoopP:j\d+>> VecPredWhile [<<Phi1>>,{{j\d+}}]       loop:<<Loop>>      outer_loop:none
  ///     CHECK-DAG:                VecAdd [<<Phi2>>,<<Rep>>,<<LoopP>>]    loop:<<Loop>>      outer_loop:none
  ///     CHECK-DAG:                Add [<<Phi1>>,{{j\d+}}]                loop:<<Loop>>      outer_loop:none
  //
  /// CHECK-ELSE:
  //
  ///     CHECK-DAG: <<L2:j\d+>>    LongConstant 2               loop:none
  ///     CHECK-DAG: <<Rep:d\d+>>   VecReplicateScalar [<<Get>>] loop:none
  ///     CHECK-DAG: <<Set:d\d+>>   VecSetScalars [<<L1>>]       loop:none
  ///     CHECK-DAG: <<Phi1:j\d+>>  Phi [<<L0>>,{{j\d+}}]        loop:<<Loop:B\d+>> outer_loop:none
  ///     CHECK-DAG: <<Phi2:d\d+>>  Phi [<<Set>>,{{d\d+}}]       loop:<<Loop>>      outer_loop:none
  ///     CHECK-DAG:                VecAdd [<<Phi2>>,<<Rep>>]    loop:<<Loop>>      outer_loop:none
  ///     CHECK-DAG:                Add [<<Phi1>>,<<L2>>]        loop:<<Loop>>      outer_loop:none
  //
  /// CHECK-FI:
  static long longInductionReduction(long[] y) {
    long x = 1;
    for (long i = 0; i < 10; i++) {
      x += y[0];
    }
    return x;
  }

  /// CHECK-START: void Main.intVectorLongInvariant(int[], long[]) loop_optimization (before)
  /// CHECK-DAG: <<I0:i\d+>>    IntConstant 0                       loop:none
  /// CHECK-DAG: <<I1:i\d+>>    IntConstant 1                       loop:none
  /// CHECK-DAG: <<Get:j\d+>>   ArrayGet [{{l\d+}},<<I0>>]          loop:none
  /// CHECK-DAG: <<Phi:i\d+>>   Phi [<<I0>>,<<Add:i\d+>>]           loop:<<Loop:B\d+>> outer_loop:none
  /// CHECK-DAG: <<Cnv:i\d+>>   TypeConversion [<<Get>>]            loop:<<Loop>>      outer_loop:none
  /// CHECK-DAG:                ArraySet [{{l\d+}},<<Phi>>,<<Cnv>>] loop:<<Loop>>      outer_loop:none
  /// CHECK-DAG: <<Add>>        Add [<<Phi>>,<<I1>>]                loop:<<Loop>>      outer_loop:none
  //
  /// CHECK-START-{X86_64,ARM64}: void Main.intVectorLongInvariant(int[], long[]) loop_optimization (after)
  /// CHECK-DAG: <<I0:i\d+>>    IntConstant 0                       loop:none
  /// CHECK-DAG: <<I1:i\d+>>    IntConstant 1                       loop:none
  /// CHECK-DAG: <<Get:j\d+>>   ArrayGet [{{l\d+}},<<I0>>]          loop:none
  /// CHECK-DAG: <<Cnv:i\d+>>   TypeConversion [<<Get>>]            loop:none
  /// CHECK-IF:     hasIsaFeature("sve") and os.environ.get('ART_FORCE_TRY_PREDICATED_SIMD') == 'true'
  //
  ///     CHECK-DAG: <<Rep:d\d+>>   VecReplicateScalar [<<Cnv>>,{{j\d+}}]         loop:none
  ///     CHECK-DAG: <<Phi:i\d+>>   Phi [<<I0>>,{{i\d+}}]                         loop:<<Loop:B\d+>> outer_loop:none
  ///     CHECK-DAG: <<LoopP:j\d+>> VecPredWhile [<<Phi>>,{{i\d+}}]               loop:<<Loop>>      outer_loop:none
  ///     CHECK-DAG:                VecStore [{{l\d+}},<<Phi>>,<<Rep>>,<<LoopP>>] loop:<<Loop>>      outer_loop:none
  ///     CHECK-DAG:                Add [<<Phi>>,{{i\d+}}]                        loop:<<Loop>>      outer_loop:none
  //
  /// CHECK-ELSE:
  //
  ///     CHECK-DAG: <<I4:i\d+>>    IntConstant 4                       loop:none
  ///     CHECK-DAG: <<Rep:d\d+>>   VecReplicateScalar [<<Cnv>>]        loop:none
  ///     CHECK-DAG: <<Phi:i\d+>>   Phi [<<I0>>,{{i\d+}}]               loop:<<Loop:B\d+>> outer_loop:none
  ///     CHECK-DAG:                VecStore [{{l\d+}},<<Phi>>,<<Rep>>] loop:<<Loop>>      outer_loop:none
  ///     CHECK-DAG:                Add [<<Phi>>,<<I4>>]                loop:<<Loop>>      outer_loop:none
  //
  /// CHECK-FI:
  static void intVectorLongInvariant(int[] x, long[] y) {
    for (int i = 0; i < 100; i++) {
      x[i] = (int) y[0];
    }
  }

  /// CHECK-START: void Main.longCanBeDoneWithInt(int[], int[]) loop_optimization (before)
  /// CHECK-DAG: <<I0:i\d+>>    IntConstant 0                        loop:none
  /// CHECK-DAG: <<I1:i\d+>>    IntConstant 1                        loop:none
  /// CHECK-DAG: <<L1:j\d+>>    LongConstant 1                       loop:none
  /// CHECK-DAG: <<Phi:i\d+>>   Phi [<<I0>>,<<Add:i\d+>>]            loop:<<Loop:B\d+>> outer_loop:none
  /// CHECK-DAG: <<Get:i\d+>>   ArrayGet [{{l\d+}},<<Phi>>]          loop:<<Loop>>      outer_loop:none
  /// CHECK-DAG: <<Cnv1:j\d+>>  TypeConversion [<<Get>>]             loop:<<Loop>>      outer_loop:none
  /// CHECK-DAG: <<AddL:j\d+>>  Add [<<Cnv1>>,<<L1>>]                loop:<<Loop>>      outer_loop:none
  /// CHECK-DAG: <<Cnv2:i\d+>>  TypeConversion [<<AddL>>]            loop:<<Loop>>      outer_loop:none
  /// CHECK-DAG:                ArraySet [{{l\d+}},<<Phi>>,<<Cnv2>>] loop:<<Loop>>      outer_loop:none
  /// CHECK-DAG: <<Add>>        Add [<<Phi>>,<<I1>>]                 loop:<<Loop>>      outer_loop:none
  //
  /// CHECK-START-{X86_64,ARM64}: void Main.longCanBeDoneWithInt(int[], int[]) loop_optimization (after)
  /// CHECK-DAG: <<I0:i\d+>>    IntConstant 0                       loop:none
  /// CHECK-DAG: <<L1:j\d+>>    LongConstant 1                      loop:none
  /// CHECK-DAG: <<Cnv:i\d+>>   TypeConversion [<<L1>>]             loop:none
  /// CHECK-IF:     hasIsaFeature("sve") and os.environ.get('ART_FORCE_TRY_PREDICATED_SIMD') == 'true'
  //
  ///     CHECK-DAG: <<Rep:d\d+>>   VecReplicateScalar [<<Cnv>>,{{j\d+}}]         loop:none
  ///     CHECK-DAG: <<Phi:i\d+>>   Phi [<<I0>>,{{i\d+}}]                         loop:<<Loop:B\d+>> outer_loop:none
  ///     CHECK-DAG: <<LoopP:j\d+>> VecPredWhile [<<Phi>>,{{i\d+}}]               loop:<<Loop>>      outer_loop:none
  ///     CHECK-DAG: <<Load:d\d+>>  VecLoad [{{l\d+}},<<Phi>>,<<LoopP>>]          loop:<<Loop>>      outer_loop:none
  ///     CHECK-DAG: <<Add:d\d+>>   VecAdd [<<Load>>,<<Rep>>,<<LoopP>>]           loop:<<Loop>>      outer_loop:none
  ///     CHECK-DAG:                VecStore [{{l\d+}},<<Phi>>,<<Add>>,<<LoopP>>] loop:<<Loop>>      outer_loop:none
  ///     CHECK-DAG:                Add [<<Phi>>,{{i\d+}}]                        loop:<<Loop>>      outer_loop:none
  //
  /// CHECK-ELSE:
  //
  ///     CHECK-DAG: <<I4:i\d+>>    IntConstant 4                       loop:none
  ///     CHECK-DAG: <<Rep:d\d+>>   VecReplicateScalar [<<Cnv>>]        loop:none
  ///     CHECK-DAG: <<Phi:i\d+>>   Phi [<<I0>>,{{i\d+}}]               loop:<<Loop:B\d+>> outer_loop:none
  ///     CHECK-DAG: <<Load:d\d+>>  VecLoad [{{l\d+}},<<Phi>>]          loop:<<Loop>>      outer_loop:none
  ///     CHECK-DAG: <<Add:d\d+>>   VecAdd [<<Load>>,<<Rep>>]           loop:<<Loop>>      outer_loop:none
  ///     CHECK-DAG:                VecStore [{{l\d+}},<<Phi>>,<<Add>>] loop:<<Loop>>      outer_loop:none
  ///     CHECK-DAG:                Add [<<Phi>>,<<I4>>]                loop:<<Loop>>      outer_loop:none
  //
  /// CHECK-FI:
  static void longCanBeDoneWithInt(int[] x, int[] y) {
    for (int i = 0; i < 100; i++) {
      x[i] = (int) (y[i] + 1L);
    }
  }

  static void testUnroll() {
    float[] x = new float[100];
    float[] y = new float[100];
    for (int i = 0; i < 100; i++) {
      x[i] = 0.0f;
      y[i] = 2.0f;
    }
    unroll(x, y);
    for (int i = 0; i < 100; i++) {
      expectEquals(5.0f, x[i]);
      expectEquals(2.0f, y[i]);
    }
  }

  private static void initArrayStencil(int[] arr) {
    for (int i = 0; i < arr.length; i++) {
      arr[i] = i;
    }
  }

  static void testStencil1() {
    int[] a = new int[100];
    int[] b = new int[100];
    initArrayStencil(b);

    stencil(a, b, 100);
    for (int i = 1; i < 99; i++) {
      int e = i + i + i;
      expectEquals(e, a[i]);
      expectEquals(i, b[i]);
    }
  }

  // Checks the disambiguation runtime test for array references.
  static void testStencilConstSize() {
    int[] a = new int[STENCIL_ARRAY_SIZE];
    int[] b = new int[STENCIL_ARRAY_SIZE];
    initArrayStencil(b);

    for (int i = 1; i < STENCIL_ARRAY_SIZE - 1; i++) {
    $noinline$stencilConstSize(a, b);
      // (i - 1) + i + (i + 1) = 3 * i.
      int e = i + i + i;
      expectEquals(e, a[i]);
      expectEquals(i, b[i]);
    }

    initArrayStencil(b);
    $noinline$stencilConstSize(b, b);

    for (int i = 1; i < STENCIL_ARRAY_SIZE - 1; i++) {
      // The formula of the ith member of recurrent def: b[i] = b[i-1] + (i) + (i+1).
      int e = i * (i + 2);
      expectEquals(e, b[i]);
    }
  }

  static void testStencil2() {
    int[] a = new int[100];
    int[] b = new int[100];
    initArrayStencil(b);

    stencilSubInt(a, b, 100);
    for (int i = 1; i < 99; i++) {
      int e = i + i + i;
      expectEquals(e, a[i]);
      expectEquals(i, b[i]);
    }
  }

  static void testStencil3() {
    int[] a = new int[100];
    int[] b = new int[100];
    initArrayStencil(b);

    stencilAddInt(a, b, 100);
    for (int i = 1; i < 99; i++) {
      int e = i + i + i;
      expectEquals(e, a[i]);
      expectEquals(i, b[i]);
    }
  }

  static void testTypes() {
    int[] a = new int[100];
    int[] b = new int[100];
    long[] l = { 3 };
    expectEquals(31, longInductionReduction(l));
    intVectorLongInvariant(a, l);
    for (int i = 0; i < 100; i++) {
      expectEquals(3, a[i]);
    }
    longCanBeDoneWithInt(b, a);
    for (int i = 0; i < 100; i++) {
      expectEquals(4, b[i]);
    }
  }

  public static void main(String[] args) {
    testUnroll();
    testStencil1();
    testStencilConstSize();
    testStencil2();
    testStencil3();
    testTypes();
    System.out.println("passed");
  }

  private static void expectEquals(int expected, int result) {
    if (expected != result) {
      throw new Error("Expected: " + expected + ", found: " + result);
    }
  }

  private static void expectEquals(long expected, long result) {
    if (expected != result) {
      throw new Error("Expected: " + expected + ", found: " + result);
    }
  }

  private static void expectEquals(float expected, float result) {
    if (expected != result) {
      throw new Error("Expected: " + expected + ", found: " + result);
    }
  }
}
