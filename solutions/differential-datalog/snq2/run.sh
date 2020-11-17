
#!/bin/bash

export PATH=$PATH:/home/qishen/ddlog/bin
export DDLOG_HOME=/home/qishen/ddlog

# Create runtime for socialnetwork domain
ddlog -i snq2.dl && 
# Open the folder and build the runtime in Rust
(cd snq2_ddlog && cargo build --release) #&& 
# Run the program that depends on the runtime as a library with model as its parameter. 
#(cd snq1_lib && cargo test --release -- it_works /home/qishen/ttc2018liveContest/models/512/ --nocapture)