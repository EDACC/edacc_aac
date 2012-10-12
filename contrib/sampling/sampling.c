#include <gsl/gsl_qrng.h>
#include <stdio.h>
#include <stdlib.h>

int main(int argc, char* argv[]) {
    if (argc < 3) {
        printf("Usage: ./sampling <dimensions> <samples>\n");
        return 0;
    }

    int dimensions = atoi(argv[1]);
    int samples = atoi(argv[2]);

    if (samples < 0) {
        printf("Invalid number of samples: %d\n", samples);
        return 0;
    }

    gsl_qrng * q;
    if (dimensions <= 0) {
        printf("Invalid number of dimensions\n");
        return 1;
    }
    else if (dimensions <= 12) q = gsl_qrng_alloc (gsl_qrng_niederreiter_2, dimensions);
    else if (dimensions <= 40) q = gsl_qrng_alloc (gsl_qrng_sobol, dimensions);
    else if (dimensions <= 1229) q = gsl_qrng_alloc (gsl_qrng_halton, dimensions);
    else {
        printf("Invalid number of dimensions (max 1229 supported): dimensions\n", dimensions);
        return 1;
    }

    for (int i = 0; i < samples; i++) {
        double v[dimensions];
        gsl_qrng_get(q, v);
        for (int d = 0; d < dimensions; d++) {
            printf("%f ", v[d]);
        }
        printf ("\n");
    }
    gsl_qrng_free(q);

    return 0;
}

