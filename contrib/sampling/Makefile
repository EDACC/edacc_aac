all: sampling

clean:
	rm -f sampling

sampling:
	gcc -std=c99 -lm -lgsl -lgslcblas -O2 -o sampling sampling.c
