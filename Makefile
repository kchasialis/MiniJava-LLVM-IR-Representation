all: compile

compile:
	java -jar jtb132di.jar minijava.jj
	java -jar javacc5.jar minijava-jtb.jj
	javac Main.java

clean:
	rm -f *.class *~
	rm **/*.class
	rm -rf syntaxtree
	rm -rf visitor
	rm -f ParseException.java
	rm -f JavaCharStream.java 
	rm -f MiniJavaParser*
	rm -f Token*
