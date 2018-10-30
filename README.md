# Medical-Drone-System
A medical drone system project for UCI EECS 159: Senior Design

---



##How to use Github for our project

###Create a Github account

Create a github account and email me the email and username you use to register Github.  
zhifanz1@uci.edu  
I will add you to the owner of our project repo.
###Install Github Desktop

Download and install Github Desktop here:
https://desktop.github.com/

After installation, you should be able to use git command directly.Try this:
>git --version

it should print sth like:
>git version 2.14.1.windows.1  

<br><br>

Optional: try if ssh is working properly: 
>ssh-v

it should print sth like:  
>OpenSSH_for_Windows_7.6p1, LibreSSL 2.6.4

<br>
<br>
Optional:install Git For Windows<br>
You are recomanded to install Git For Windows here: https://gitforwindows.org/ <br>
It will accelerate your development.

###Setup local environment
Open cmd, cd to your project directory and type:
>git init

It will create a .git diretory for you.  
Then let`s bind the project to our remote project repo:
>git remote add origin https://github.com/MICHAEL-ZENGZF/Medical-Drone-System.git

Before you start working, pull the repo:
>git pull

If you want to build a new branch, input:
>git checkout -b new_branch_name

Now you are in a new branch. You can now begin your work.  
If you want to upload to our repo, try:
>git add *<br>
>git commit -m "First Commit"

If you are going to push for the first time, since there is no such branch as you just
created, remember to input:
>git push --set-upstream origin new_branch_name

After that you can just input:
>git push

to upload your file

Optional:
You can use ssh to push without inputing your username and password every time.  
How ever,using cmd directly doesn`t work currently as a result of win10 update.
Thus, you need Git For Windows to use ssh.  

Suppose you use Git Bash from now.

First, Download the private key MedicalDrone from master branch.  
Second, create the directory ~/.ssh and copy the MedicalDrone file to this directory.  
Third, create a file ~/.bashrc, paste the following and save:
env=~/.ssh/agent.env

>agent_load_env () { test -f "$env" && . "$env" >| /dev/null ; }
>
>agent_start () {
    (umask 077; ssh-agent >| "$env")
    . "$env" >| /dev/null ; }
>
>agent_load_env
>
>\# agent_run_state: 0=agent running w/ key; 1=agent w/o key; 2= agent not running
>agent_run_state=$(ssh-add -l >| /dev/null 2>&1; echo $?)
>
>if \[ ! "$SSH_AUTH_SOCK" ] || \[ $agent_run_state = 2 ]; then
    agent_start
    ssh-add
>elif \[ "$SSH_AUTH_SOCK" ] && \[ $agent_run_state = 1 ]; then
    ssh-add
>fi
>ssh-add ~/.ssh/MedicalDrone
>
>unset env

Restart the git bash and now you should be able to access our repo with ssh.  
To do this, go to your project directory and input:
>git remote set-url origin git@github.com:MICHAEL-ZENGZF/Medical-Drone-System.git

Now you should be able to push directly.