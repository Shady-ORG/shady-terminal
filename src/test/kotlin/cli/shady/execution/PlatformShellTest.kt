package cli.shady.execution

import kotlin.test.Test
import kotlin.test.assertEquals

class PlatformShellTest {
    @Test
    fun `builds a unix shell invocation`() {
        val invocation = PlatformShell("Mac OS X").invocationFor("printf '%s' hello")

        assertEquals(listOf("/bin/sh", "-lc", "printf '%s' hello"), invocation)
    }

    @Test
    fun `builds a windows shell invocation`() {
        val invocation = PlatformShell("Windows 11").invocationFor("python tool.py")

        assertEquals(listOf("cmd.exe", "/d", "/s", "/c", "python tool.py"), invocation)
    }
}
