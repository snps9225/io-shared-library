# io-shared-library

Jenkins shared library for integration of Synopsys tools with Jenkins CI

Code Snippet for Jenkinsfile
```
@Library('io-library')
import com.synopsys.*
new pipeline.SecurityPipeline().execute()
```
