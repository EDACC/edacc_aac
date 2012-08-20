require(scatterplot3d)

data = read.csv("plot.data", sep=" ")

scatterplot3d(-data[,1], -data[,2], log10(data[,5]), highlight.3d=T)