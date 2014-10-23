default: ImageCompressor.class

ImageCompressor.class: ImageCompressor.java
	javac ImageCompressor.java

run:
	java ImageCompressor image1.rgb -1

clean:
	rm *.class