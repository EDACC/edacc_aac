require(scatterplot3d)

data = read.csv("plot.data", sep=" ")

scatterplot3d(-data[,1], -data[,2], -log10(sqrt(data[,3])), highlight.3d=T)