 #!/bin/bash
 echo "Testing1" >> README.md
 git add .
 git commit -m "Updating README.me"
 git tag $1
 git push
 git push --tags
