package dev.me_cafebabe.wakeladder.ui.task

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.me_cafebabe.wakeladder.R
import kotlin.random.Random

/** Solve [PROBLEMS] small arithmetic problems. Wrong answers just clear the field. */
@Composable
fun MathTask(onDone: () -> Unit) {
    var solved by remember { mutableIntStateOf(0) }
    var problem by remember { mutableStateOf(newProblem()) }
    var input by remember { mutableStateOf("") }

    Column {
        Text(pluralStringResource(R.plurals.task_math_left, PROBLEMS - solved, PROBLEMS - solved), style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(12.dp))
        Text("${problem.a} × ${problem.b} + ${problem.c} = ?", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = input,
            onValueChange = { v ->
                input = v.filter(Char::isDigit).take(5)
                if (input.toIntOrNull() == problem.answer) {
                    solved++
                    input = ""
                    if (solved >= PROBLEMS) onDone() else problem = newProblem()
                }
            },
            label = { Text(stringResource(R.string.task_math_answer)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private const val PROBLEMS = 3

internal data class Problem(val a: Int, val b: Int, val c: Int) {
    val answer get() = a * b + c
}

internal fun newProblem(random: Random = Random) = Problem(random.nextInt(6, 15), random.nextInt(3, 10), random.nextInt(5, 40))
