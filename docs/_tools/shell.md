---
title: "Rhino shell"
---
# Rhino shell
{: .no_toc }

{: .fs-6 .fw-300 }

## Table of contents
{: .no_toc .text-delta }

1. TOC
{:toc}

---
The JavaScript shell provides a simple way to run scripts in batch mode or an interactive environment for exploratory programming.

### Invoking the Shell

`java org.mozilla.javascript.tools.shell.Main [_options_] _script-filename-or-url_ [_script-arguments_]`

where `_options_` are:

#### `-e _script-source_`

Executes _script-source_ as a JavaScript script.

#### `-f _script-filename-or-url_`

Reads _script-filename-or-url_ content and execute it as a JavaScript script.

#### `-opt _optLevel_` / `-O _optLevel_`

Optimizes at level _optLevel_, which must be `-1` or an integer between `0` and `9`. See (Rhino Optimization)[/en-US/docs/Mozilla/Projects/Rhino/Optimization] for more details.

#### `-version _versionNumber_`

Specifies the language version to compile with. The string _versionNumber_ must be one of `100`, `110`, `120`, `130`, `140`, `150`, `160 or 170`. See (JavaScript Language Versions)[/en-US/docs/Mozilla/Projects/Rhino/Overview#JavaScript_Language_Versions] for more information on language versions.

#### `-strict`

Enable strict mode.

#### `-continuations`

Enable experimental support for continuations and set the optimization level to -1 to force interpretation mode. Starting with Rhino 1.7 this options is no longer available.

#### Note

If the shell is invoked with the system property `rhino.use_java_policy_security` set to `true` and with a security manager installed, the shell restricts scripts permissions based on their URLs according to Java policy settings. This is available only if JVM implements Java2 security model.

### Predefined Properties

Scripts executing in the shell have access to some additional properties of the top-level object.

#### `arguments`

The `arguments` object is an array containing the strings of all the arguments given at the command line when the shell was invoked.

#### `environment`

Returns the current environment object.

#### `history`

Displays the shell command history.

#### `help()`

Executing the `help` function will print usage and help messages.

#### `defineClass(_className_)`

Define an extension using the Java class named with the string argument _className_. Uses `ScriptableObject.defineClass()` to define the extension.

#### `deserialize(_filename_)`

Restore from the specified file an object previously written by a call to `serialize`.

#### `gc()`

Runs the garbage collector.

#### `load([_filename_, ...])`

Load JavaScript source files named by string arguments. If multiple arguments are given, each file is read in and executed in turn.

#### `loadClass(_className_)`

Load and execute the class named by the string argument _className_. The class must be a class that implements the Script interface, as will any script compiled by (Rhino JavaScript Compiler)[/en-US/docs/Mozilla/Projects/Rhino/JavaScript_Compiler].

#### `print([_expr_ ...])`

Evaluate and print expressions. Evaluates each expression, converts the result to a string, and prints it.

#### `readFile(_path_ [, _characterCoding]_)`

Read given file and convert its bytes to a string using the specified character coding or default character coding if explicit coding argument is not given.

#### `readUrl(_url_ [, _characterCoding_])`

Open an input connection to the given string url, read all its bytes and convert them to a string using the specified character coding or default character coding if explicit coding argument is not given.

#### `runCommand(_commandName_, [_arg_, ...] [_options_])`

Execute the specified command with the given argument and options as a separate process and return the exit status of the process.

Usage:

```
runCommand(command)
runCommand(command, arg1, ..., argN)
runCommand(command, arg1, ..., argN, options)
```

All except the last arguments to `runCommand` are converted to strings and denote command name and its arguments. If the last argument is a JavaScript object, it is an option object. Otherwise it is converted to string denoting the last argument and options objects assumed to be empty.

The following properties of the option object are processed:

- `args` - provides an array of additional command arguments
- `env` - explicit environment object. All its enumeratable properties define the corresponding environment variable names.
- `input` - the process input. If it is not `java.io.InputStream`, it is converted to string and sent to the process as its input. If not specified, no input is provided to the process.
- `output` - the process output instead of `java.lang.System.out`. If it is not instance of `java.io.OutputStream`, the process output is read, converted to a string, appended to the output property value converted to string and put as the new value of the output property.
- `err` - the process error output instead of `java.lang.System.err`. If it is not instance of `java.io.OutputStream`, the process error output is read, converted to a string, appended to the err property value converted to string and put as the new value of the err property.

#### `seal(_object_)`

Seal the specified object so any attempt to add, delete or modify its properties would throw an exception.

#### `serialize(_object_, _filename_)`

Serialize the given object to the specified file.

#### `spawn(_functionOrScript_)`

Run the given function or script in a different thread.

#### `sync(_function_)`

creates a synchronized function (in the sense of a Java `synchronized` method) from an existing function. The new function synchronizes on the `this` object of its invocation.

#### `quit()`

Quit shell. The shell will also quit in interactive mode if an end-of-file character is typed at the prompt.

#### `version([_number_])`

Get or set JavaScript version number. If no argument is supplied, the current version number is returned. If an argument is supplied, it is expected to be one of `100`, `110`, `120`, `130`, `140`, `150`, `160 or 170` to indicate JavaScript version 1.0, 1.1, 1.2, 1.3, 1.4, 1.5, 1.6 or 1.7 respectively.

### Examples

#### Invocation

Here the shell is invoked three times from the command line. (The system command prompt is shown as `$`.) The first invocation executes a script specified on the command line itself. The next invocation has no arguments, so the shell goes into interactive mode, reading and evaluating each line as it is typed in. Finally, the last invocation executes a script from a file and accesses arguments to the script itself.

```
$ java org.mozilla.javascript.tools.shell.Main -e "print('hi')"
hi
$ java org.mozilla.javascript.tools.shell.Main
js> print('hi')
hi
js> 6*7
42
js> function f() {
  return a;
}
js> var a = 34;
js> f()
34
js> quit()
$ cat echo.js
for (i in arguments) {
  print(arguments[i])
}
$ java org.mozilla.javascript.tools.shell.Main echo.js foo bar
foo
bar
$
```

#### `spawn` and `sync`

The following example creates 2 threads via `spawn` and uses `sync` to create a synchronized version of the function `test`.

```
js> function test(x) {
  print("entry");
  java.lang.Thread.sleep(x*1000);
  print("exit");
}
js> var o = { f : sync(test) };
js> spawn(function() {o.f(5);});
Thread[Thread-0,5,main]
entry
js> spawn(function() {o.f(5);});
Thread[Thread-1,5,main]
js>
exit
entry
exit
```

#### `runCommand`

Here are a few examples of invoking `runCommand` under Linux.

```
js> runCommand('date')
Thu Jan 23 16:49:36 CET 2003
0
// Using input option to provide process input
js> runCommand("sort", {input: "c\na\nb"})
a
b
c
0
js> // Demo of output and err options
js> var opt={input: "c\na\nb", output: 'Sort Output:\n'}
js> runCommand("sort", opt)
0
js> print(opt.output)
Sort Output:
a
b
c
js> var opt={input: "c\na\nb", output: 'Sort Output:\n', err: ''}
js> runCommand("sort", "--bad-arg", opt)
2
js> print(opt.err)
/bin/sort: unrecognized option `--bad-arg'
Try `/bin/sort --help' for more information.

js> runCommand("bad_command", "--bad-arg", opt)
js: "<stdin>", line 18: uncaught JavaScript exception: java.io.IOException: bad_command: not found
js> // Passing explicit environment to the system shell
js> runCommand("sh", "-c", "echo $env1 $env2", { env: {env1: 100, env2: 200}})
100 200
0
js> // Use args option to provide additional command arguments
js> var arg_array = [1, 2, 3, 4];
js> runCommand("echo", { args: arg_array})
1 2 3 4
0
```

Examples for Windows are similar:

```
js> // Invoke shell command
js> runCommand("cmd", "/C", "date /T")
27.08.2005
0
js> // Run sort collectiong the output
js> var opt={input: "c\na\nb", output: 'Sort Output:\n'}
js> runCommand("sort", opt)
0
js> print(opt.output)
Sort Output:
a
b
c
js> // Invoke notepad and wait until it exits
js> runCommand("notepad")
0
```