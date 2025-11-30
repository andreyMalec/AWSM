package com.malec.awsm

sealed interface ASM {

    /**
     * Marks the target belongs to TuringComplete specific capability
     */
    @Retention(AnnotationRetention.SOURCE)
    @Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
    annotation class PlatformSpecific

    sealed class ThreeArgumentInstruction : ASM {
        open val name: String
            get() = this::class.simpleName.toString()

        abstract val dst: Argument
        abstract val a: Argument
        abstract val b: Argument

        final override fun toString(): String {
            return "$name $dst, $a, $b"
        }
    }

    sealed class TwoArgumentInstruction : ASM {
        open val name: String
            get() = this::class.simpleName.toString()

        abstract val a: Argument
        abstract val b: Argument

        final override fun toString(): String {
            return "$name $a, $b"
        }
    }

    sealed class OneArgumentInstruction : ASM {
        open val name: String
            get() = this::class.simpleName.toString()

        abstract val a: Argument

        final override fun toString(): String {
            return "$name $a"
        }
    }

    sealed class NoArgumentInstruction : ASM {
        open val name: String
            get() = this::class.simpleName.toString()

        final override fun toString(): String {
            return name
        }
    }

    data class MUL(
        override val dst: Argument.Register,
        override val a: Argument.Register,
        override val b: Argument.Register
    ) : ThreeArgumentInstruction()

    data class MOD(
        override val dst: Argument.Register,
        override val a: Argument.Register,
        override val b: Argument.Register
    ) : ThreeArgumentInstruction()

    data class OR(
        override val dst: Argument.Register,
        override val a: Argument.Register,
        override val b: Argument
    ) : ThreeArgumentInstruction()

    data class NAND(
        override val dst: Argument.Register,
        override val a: Argument.Register,
        override val b: Argument
    ) : ThreeArgumentInstruction()

    data class NOR(
        override val dst: Argument.Register,
        override val a: Argument.Register,
        override val b: Argument
    ) : ThreeArgumentInstruction()

    data class AND(
        override val dst: Argument.Register,
        override val a: Argument.Register,
        override val b: Argument
    ) : ThreeArgumentInstruction()

    data class XOR(
        override val dst: Argument.Register,
        override val a: Argument.Register,
        override val b: Argument
    ) : ThreeArgumentInstruction()

    data class ADD(
        override val dst: Argument.Register,
        override val a: Argument.Register,
        override val b: Argument
    ) : ThreeArgumentInstruction()

    data class SUB(
        override val dst: Argument.Register,
        override val a: Argument.Register,
        override val b: Argument
    ) : ThreeArgumentInstruction()

    data class LSL(
        override val dst: Argument.Register,
        override val a: Argument.Register,
        override val b: Argument
    ) : ThreeArgumentInstruction()

    data class LSR(
        override val dst: Argument.Register,
        override val a: Argument.Register,
        override val b: Argument
    ) : ThreeArgumentInstruction()

    data class CMP(
        override val a: Argument.Register,
        override val b: Argument
    ) : TwoArgumentInstruction()

    data class JMP(
        override val a: Argument
    ) : OneArgumentInstruction()

    /**
     * Jump to [a] if the values were equal (reading results from 'flags').
     */
    data class JE(
        override val a: Argument
    ) : OneArgumentInstruction()

    /**
     * Jump to [a] if the values were not equal (reading results from 'flags').
     */
    data class JNE(
        override val a: Argument
    ) : OneArgumentInstruction()

    /**
     * a < b
     *
     * Jump to [a] if the first value was below (unsigned) the second (reading results from 'flags').
     */
    data class JB(
        override val a: Argument
    ) : OneArgumentInstruction()

    /**
     * a <= b
     *
     * Jump to [a] if the First value was below (unsigned) or equal to the second (reading results from 'flags').
     */
    data class JBE(
        override val a: Argument
    ) : OneArgumentInstruction()

    /**
     * a > b
     *
     * Jump to [a] if the first value was above (unsigned) the second (reading results from 'flags').
     */
    data class JA(
        override val a: Argument
    ) : OneArgumentInstruction()

    /**
     * a >= b
     *
     * Jump to [a] if the first value was above (unsigned) or euqal to the second (reading results from 'flags').
     */
    data class JAE(
        override val a: Argument
    ) : OneArgumentInstruction()

    /**
     * Loads keyboard input and stores it to [a]
     */
    @PlatformSpecific
    data class KEYBOARD(
        override val a: Argument.Register
    ) : OneArgumentInstruction()

    /**
     * Stores the value of [a] in the console offset register
     */
    @PlatformSpecific
    data class CONSOLE(
        override val a: Argument
    ) : OneArgumentInstruction()

    /**
     * Loads the first two bytes of the time value and stores it to [a]
     */
    @PlatformSpecific
    data class TIME_0(
        override val a: Argument.Register
    ) : OneArgumentInstruction()

    /**
     * Loads the second two bytes of the time value and stores it to [a]
     */
    @PlatformSpecific
    data class TIME_1(
        override val a: Argument.Register
    ) : OneArgumentInstruction()

    /**
     * Loads the third two bytes of the time value and stores it to [a]
     */
    @PlatformSpecific
    data class TIME_2(
        override val a: Argument.Register
    ) : OneArgumentInstruction()

    /**
     * Loads the fourth two bytes of the time value and stores it to [a]
     */
    @PlatformSpecific
    data class TIME_3(
        override val a: Argument.Register
    ) : OneArgumentInstruction()

    /**
     * Loads the counter value and stores it to [a]
     */
    @PlatformSpecific
    data class COUNTER(
        override val a: Argument.Register
    ) : OneArgumentInstruction()

    /**
     * Loads [address] from RAM and stores it to [dst]
     */
    data class LOAD(
        val dst: Argument.Register,
        val address: Argument.Address
    ) : ASM {
        val name: String
            get() = this::class.simpleName.toString()

        override fun toString(): String {
            return "$name $dst, $address"
        }
    }

    /**
     * Store [value] to [address] in RAM
     */
    data class STORE(
        val address: Argument.Address,
        val value: Argument.Register
    ) : ASM {
        val name: String
            get() = this::class.simpleName.toString()

        override fun toString(): String {
            return "$name $address, $value"
        }
    }

    /**
     * Loads [address] from SSD and stores it to [dst]
     */
    @PlatformSpecific
    data class PLOAD(
        val dst: Argument.Register,
        val address: Argument.Address
    ) : ASM {
        val name: String
            get() = this::class.simpleName.toString()

        override fun toString(): String {
            return "$name $dst, $address"
        }
    }

    /**
     * Store [value] to [address] in SSD
     */
    @PlatformSpecific
    data class PSTORE(
        val address: Argument.Address,
        val value: Argument.Register
    ) : ASM {
        val name: String
            get() = this::class.simpleName.toString()

        override fun toString(): String {
            return "$name $address, $value"
        }
    }

    /**
     * Move from [a] to [b]
     */
    data class MOV(
        override val a: Argument.Register,
        override val b: Argument
    ) : TwoArgumentInstruction()

    /**
     * Neg [b] and store the result in [a]
     */
    data class NEG(
        override val a: Argument.Register,
        override val b: Argument
    ) : TwoArgumentInstruction()

    /**
     * Not [b] and store the result in [a]
     */
    data class NOT(
        override val a: Argument.Register,
        override val b: Argument
    ) : TwoArgumentInstruction()

    /**
     * Pushes [a] on to the stack.
     *
     * This instruction is a synonym for:   sub sp, sp, 2   store [sp], %a
     */
    data class PUSH(
        override val a: Argument.Register
    ) : OneArgumentInstruction()

    /**
     * Pops [a] off the stack.
     *
     * This instruction is a synonym for:   load %a, [sp]   add sp, sp, 2
     */
    data class POP(
        override val a: Argument.Register
    ) : OneArgumentInstruction()

    /**
     * Calls [a]
     *
     * Overwrites the flag register. This instruction is a synonym for:   counter flags   add flags, flags, 20   sub sp, sp, 2   store [sp], flags   jmp %a
     */
    data class CALL(
        override val a: Argument
    ) : OneArgumentInstruction()

    /**
     * Returns from the last function call.
     *
     * Overwrites the flag register. This instruction is a synonym for:   load flags, [sp]   add sp, sp, 2   jmp flags
     */
    data object RET : NoArgumentInstruction()

    /**
     * Loads an input and stores it in [a]
     */
    @PlatformSpecific
    data class IN(
        override val a: Argument.Register
    ) : OneArgumentInstruction()

    /**
     * Sends [a] to output
     */
    @PlatformSpecific
    data class OUT(
        override val a: Argument.Register
    ) : OneArgumentInstruction()

    data class LABEL(val name: String) : ASM {
        override fun toString(): String {
            return "${name}:"
        }

        companion object {
            operator fun invoke(name: Argument.Value) = LABEL(name.value.toString())
        }
    }

    data object NOP : NoArgumentInstruction()
}