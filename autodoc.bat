rem asdf
set GOODOLDPATH=%PATH%
set PATH=C:\Native\Git\bin;C:\Java\JRE\bin
java -cp "../../autodoc/autodoc-0.8.0-SNAPSHOT-standalone.jar;../src;../lib/dev/swt-win32-win32-x86_64-3.5.2.jar" autodoc.autodoc --name "GUI FTW!" --description "Declarative GUI framework for Clojure" --web-src-dir "http://github.com/santamon/GUIFTW/blob/" --external-doc-tmpdir tmp --copyright "2011 Szymon Witamborski, MIT Licence" --root .. --output-path .
set PATH=%GOODOLDPATH%
