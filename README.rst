EDACC Automated Algorithm Configuration
=======================================

Experiment Design and Administration for Computer Clusters for SAT Solvers.
See http://sourceforge.net/projects/edacc/ for the EDACC project.

Description
-----------

This tool can be used to optimize the empirical performance of algorithms
on problem instances automatically by adjusting free parameters of the algorithms.

Build
-----

Compile by running "ant" in the top level directory containing "build.xml".
An executable Java JAR file will be put into the folder "dist/aac/"

How to use
----------

- The tool requires a network connection to an EDACC database and a configuration
  experiment that was set up using the EDACC GUI. Computation clients should also be
  started and assigned to the configuration experiment using the GUI.
- Write a configuration file that tells the tool which optimisation procedure
  to use, the database connection details, optimisation objective etc. See the "contrib/"
  folder for examples.
- Start the process via "java -jar AAC.jar configfile"

Dependencies
------------

- Java (JRE) 6 or 7
- some procedures need R (tested with version 2.15), JRI and certain R packages
  (they will tell which at runtime)
  
EDACC-MBO Dependencies
----------------------

The SMBO/Default_SMBO optimization procedure requires a working R installation with the
Java-R-interface "JRI" and some R packages.

- JRI for R can be installed in R itself by using the command install.packages("rJava").
  See also http://www.rforge.net/JRI/ and the FAQ at the bottom for adjusting environment
  variables (in particular LD_LIBRARY_PATH) so Java can access JRI.
- The R packages "asbio", "cluster" and "survival" can be installed in an R shell via install.packages("pkgname")

