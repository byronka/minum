Webapp
=========

These files are not meant to be bundled in the jar but are available as 
directories when we run the application

* _static_: files that never change during the runtime. Site graphics, scripts, etc.
            These are made directly available to the browser, like if we have a file
            in here foo.js, then a user could get http://domain.com/foo.js
* _templates_: files with values that get replaced dynamically during the runtime. These
               are not made directly available to the browser, they are expected to be
               read by the program.