# MiniJava-LLVM-IR-Representation
MiniJava to LLVM IR Representation for Compilers course.

[LLVM Language Reference Manual](https://llvm.org/docs/LangRef.html#instruction-reference)

Compile : make  
Execute : java Main java_file1.java java_file2.java java_file3.java java_file4.java ... java_fileN.java  

This will produce java_file*.ll files which in turn can be compiled and executed as follows :  
Compile : clang java_file1.ll -o java_file1  
Execute : ./java_file1
