require(scatterplot3d)

f = function(x) {
    val = system(paste("/home/daniel/development/workspace/bbo/bbfunc 123", sample(1:100000, 1), "f101 1 2.131", x), intern=T)
    return (as.double(strsplit(val, c(" "))[[1]][[2]]))
}


data = read.csv("plot.data", sep=" ")

f_true = c()
for (i in seq(-5, 5, 0.1)) {
	f_true = c(f_true, f(i))
}

x_eval = c(2.5, 0.0, -3.75, -5, -3.125, 1.25, 3.75, -2.5, 1.875, -1.25)

sigma = sqrt(data[,3])
mu = data[,2]
f_min = min(data[,2])
x = (f_min - mu) / sigma
ei = (f_min - mu) * pnorm(x) + sigma * dnorm(x)
ei2 = sigma*sigma * ((x*x + 1) * pnorm(x) + x * dnorm(x));

ylim=c(min(data[,2]), max(data[,2]))

plot(seq(-5, 5, 0.1), f_true, type='l', col='black', xlim=c(-5, 5), ylim=ylim, xlab='', ylab='')
par(new=T)
plot(data[,1], data[,2], type='l', col='red', xlim=c(-5, 5), ylim=ylim, xlab='', ylab='', axes=F)
par(new=T)
plot(x_eval, vapply(x_eval, f, c(42)), lwd=3, col='blue', xlim=c(-5, 5),ylim=ylim, xlab='', ylab='', axes=F)
par(new=T)
plot(data[,1], mu + 2*sigma, type='l', col='grey', xlim=c(-5, 5), ylim=ylim, xlab='', ylab='', axes=F)
par(new=T)
plot(data[,1], mu -  2*sigma, type='l', col='grey', xlim=c(-5, 5),ylim=ylim, xlab='', ylab='', axes=F)
par(new=T)
plot(data[,1], 10+2*( -mu + 1*sigma), type='l', col='green', xlim=c(-5, 5),ylim=ylim, xlab='', ylab='', axes=F)
par(new=T)
plot(data[,1], 100000*ei, type='l', col='cyan', xlim=c(-5, 5), ylim=ylim, xlab='', ylab='', axes=F)
par(new=T)
plot(data[,1], 1000000*ei2, type='l', col='blue', xlim=c(-5, 5), ylim=ylim, xlab='', ylab='', axes=F)