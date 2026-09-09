// Generates modules/*/pom.xml from the dependency table below. Internal module names are bare
// (e.g. "kicool"); everything else is an external artifact managed in the parent pom.
import fs from 'node:fs'
import path from 'node:path'

const root = path.resolve(new URL('.', import.meta.url).pathname, '..')
const GROUP = 'de.cau.cs.kieler.lite'

// Xtext generator fragments referenced from a few sources; compile-only.
const PROVIDED = new Set(['org.eclipse.xtext.xtext.generator'])

const MODULES = {
  core: ['guava', 'org.eclipse.xtext.xbase.lib', 'org.eclipse.xtend.lib', 'org.eclipse.xtend.lib.macro', 'org.eclipse.emf.ecore', 'org.eclipse.core.runtime', 'org.eclipse.xtext', 'org.eclipse.xtext.xtext.generator'],
  annotations: ['guava', 'guice', 'org.eclipse.xtext.xtext.generator', 'commons-logging', 'org.eclipse.xtext.common.types', 'org.eclipse.xtext.xbase.lib', 'antlr-runtime', 'org.eclipse.xtext.util', 'org.eclipse.xtext', 'org.eclipse.emf.ecore', 'org.eclipse.xtend.lib'],
  'annotations.ide': ['annotations', 'org.eclipse.xtext.ide', 'de.cau.cs.kieler.klighd.kgraph', 'org.eclipse.xtend.lib', 'org.eclipse.xtend.lib.macro'],
  kexpressions: ['annotations', 'org.eclipse.xtext', 'org.eclipse.xtext.common.types', 'org.eclipse.xtext.xtext.generator', 'org.eclipse.xtext.util', 'commons-logging', 'antlr-runtime', 'org.eclipse.xtext.xbase.lib', 'org.eclipse.xtend.lib', 'org.eclipse.xtend.lib.macro'],
  'kexpressions.ide': ['kexpressions', 'org.eclipse.xtext.ide', 'org.eclipse.xtend.lib', 'org.eclipse.xtend.lib.macro'],
  kicool: ['annotations', 'kexpressions', 'core', 'org.eclipse.xtext', 'org.eclipse.xtext.util', 'antlr-runtime', 'org.eclipse.xtext.xbase.lib', 'org.eclipse.xtend.lib', 'org.eclipse.xtend.lib.macro', 'org.eclipse.core.runtime', 'org.eclipse.core.resources', 'gson', 'freemarker'],
  'kicool.ide': ['kicool', 'org.eclipse.xtext.ide', 'guava', 'org.eclipse.xtend.lib', 'org.eclipse.xtend.lib.macro', 'org.eclipse.core.runtime', 'reload4j', 'de.cau.cs.kieler.klighd', 'de.cau.cs.kieler.klighd.ide', 'org.eclipse.elk.alg.common', 'org.eclipse.elk.core', 'de.cau.cs.kieler.klighd.setup'],
  scg: ['annotations', 'kexpressions', 'kicool', 'org.eclipse.xtend.lib', 'org.eclipse.xtend.lib.macro', 'guava', 'guice', 'org.eclipse.core.resources', 'org.eclipse.core.runtime', 'org.eclipse.emf.ecore.xmi'],
  scl: ['kexpressions', 'kicool', 'scg', 'org.eclipse.xtext', 'guava', 'org.eclipse.xtext.xbase.lib', 'org.eclipse.xtend.lib', 'org.eclipse.xtend.lib.macro', 'antlr-runtime', 'org.eclipse.xtext.util'],
  'scl.ide': ['scl', 'core', 'kexpressions.ide', 'annotations.ide', 'org.eclipse.xtext.ide', 'org.eclipse.xtend.lib', 'org.eclipse.xtend.lib.macro'],
  sccharts: ['annotations', 'kexpressions', 'scl', 'scg', 'kicool', 'org.eclipse.xtend.lib', 'org.eclipse.xtend.lib.macro', 'guava', 'guice', 'org.eclipse.xtext', 'org.eclipse.xtext.xtext.generator', 'org.eclipse.xtext.util', 'org.eclipse.emf.common', 'antlr-runtime', 'commons-logging', 'org.eclipse.core.resources', 'org.eclipse.core.runtime', 'org.eclipse.elk.graph', 'org.eclipse.elk.core'],
  simulation: ['org.eclipse.core.resources', 'guava', 'org.eclipse.xtend.lib', 'org.eclipse.xtend.lib.macro', 'org.eclipse.xtext.xtext.generator', 'org.eclipse.xtext', 'gson', 'org.eclipse.xtext.util', 'org.eclipse.emf.ecore', 'org.eclipse.emf.common', 'antlr-runtime', 'kexpressions', 'kicool', 'commons-logging'],
  'simulation.ide': ['org.eclipse.xtext.ide', 'simulation', 'kicool', 'gson', 'org.eclipse.elk.core', 'de.cau.cs.kieler.klighd.krendering.extensions', 'de.cau.cs.kieler.klighd.ide', 'antlr-runtime', 'org.eclipse.xtend.lib', 'org.eclipse.xtend.lib.macro'],
  'sccharts.ide': ['org.eclipse.xtext.ide', 'sccharts', 'core', 'de.cau.cs.kieler.klighd', 'simulation', 'kicool', 'simulation.ide', 'scl.ide', 'gson', 'kicool.ide', 'org.eclipse.xtend.lib', 'org.eclipse.xtend.lib.macro'],
  // Carved down to the headless synthesis parts; see README.
  'kicool.ui': ['org.eclipse.core.runtime', 'annotations', 'kicool', 'kicool.ide', 'annotations.ide', 'kexpressions', 'kexpressions.ide', 'de.cau.cs.kieler.klighd', 'de.cau.cs.kieler.klighd.ide', 'de.cau.cs.kieler.klighd.krendering.extensions', 'org.eclipse.elk.alg.layered', 'guava', 'org.eclipse.xtend.lib', 'org.eclipse.xtend.lib.macro', 'swt-mock-ui', 'kgraph.text'],
  'sccharts.ui': ['sccharts', 'sccharts.ide', 'scg', 'kicool', 'kicool.ui', 'kicool.ide', 'simulation.ide', 'annotations.ide', 'de.cau.cs.kieler.klighd', 'de.cau.cs.kieler.klighd.ide', 'de.cau.cs.kieler.klighd.kgraph', 'de.cau.cs.kieler.klighd.krendering.extensions', 'org.eclipse.elk.graph', 'org.eclipse.elk.core', 'org.eclipse.elk.alg.layered', 'org.eclipse.elk.alg.force', 'org.eclipse.elk.alg.rectpacking', 'gson', 'org.eclipse.xtend.lib', 'org.eclipse.xtend.lib.macro', 'swt-mock-ui', 'swt-mock-jface', 'swt-mock-swt'],
  'scg.klighd': ['org.eclipse.elk.alg.layered', 'org.eclipse.elk.alg.mrtree', 'de.cau.cs.kieler.klighd', 'de.cau.cs.kieler.klighd.krendering.extensions', 'scg', 'kicool', 'kicool.ide', 'kicool.ui', 'simulation.ide', 'annotations.ide', 'guava', 'guice', 'org.eclipse.xtext', 'org.eclipse.xtend.lib', 'org.eclipse.xtend.lib.macro'],
  'language.server': ['core', 'kicool', 'kicool.ide', 'kicool.ui', 'sccharts', 'sccharts.ide', 'sccharts.ui', 'scg', 'scg.klighd', 'simulation', 'simulation.ide', 'de.cau.cs.kieler.klighd.lsp', 'org.eclipse.elk.alg.layered', 'org.eclipse.elk.alg.mrtree', 'org.eclipse.elk.alg.rectpacking', 'org.eclipse.xtext', 'org.eclipse.xtext.ide', 'org.eclipse.xtext.xbase.lib', 'org.eclipse.xtend.lib', 'gson', 'guice', 'reload4j', 'org.eclipse.lsp4j', 'org.eclipse.lsp4j.jsonrpc', 'org.eclipse.core.runtime', 'org.eclipse.equinox.common', 'freemarker'],
}

// External artifact coordinates. Versions live in the parent pom's dependencyManagement.
const EXTERNAL = {
  guava: ['com.google.guava', 'guava'],
  guice: ['com.google.inject', 'guice'],
  gson: ['com.google.code.gson', 'gson'],
  'commons-logging': ['commons-logging', 'commons-logging'],
  'antlr-runtime': ['org.antlr', 'antlr-runtime'],
  freemarker: ['org.freemarker', 'freemarker'],
  reload4j: ['ch.qos.reload4j', 'reload4j'],
  'log4j-core': ['org.apache.logging.log4j', 'log4j-core'],
  'swt-mock-ui': ['de.cau.cs.kieler.swt.mock', 'org.eclipse.ui'],
  'swt-mock-jface': ['de.cau.cs.kieler.swt.mock', 'org.eclipse.jface'],
  'swt-mock-swt': ['de.cau.cs.kieler.swt.mock', 'org.eclipse.swt'],
  'kgraph.text': ['de.cau.cs.kieler.klighd', 'de.cau.cs.kieler.kgraph.text'],
}
function coords(name) {
  if (EXTERNAL[name]) return EXTERNAL[name]
  if (name.startsWith('org.eclipse.xtext')) return ['org.eclipse.xtext', name]
  if (name.startsWith('org.eclipse.xtend')) return ['org.eclipse.xtend', name]
  if (name.startsWith('org.eclipse.emf')) return ['org.eclipse.emf', name]
  if (name.startsWith('org.eclipse.elk')) return ['org.eclipse.elk', name]
  if (name.startsWith('org.eclipse.lsp4j')) return ['org.eclipse.lsp4j', name]
  if (name.startsWith('org.eclipse.core') || name.startsWith('org.eclipse.equinox')) return ['org.eclipse.platform', name]
  if (name.startsWith('de.cau.cs.kieler.klighd')) return ['de.cau.cs.kieler.klighd', name]
  throw new Error(`unknown artifact ${name}`)
}

for (const [name, deps] of Object.entries(MODULES)) {
  const dir = path.join(root, 'modules', name)
  const has = (d) => fs.existsSync(path.join(dir, d))
  const dependencies = deps.map((dep) => {
    if (MODULES[dep]) return `    <dependency><groupId>${GROUP}</groupId><artifactId>${dep}</artifactId><version>\${project.version}</version></dependency>`
    const [g, a] = coords(dep)
    const scope = PROVIDED.has(dep) ? '<scope>provided</scope>' : ''
    return `    <dependency><groupId>${g}</groupId><artifactId>${a}</artifactId>${scope}</dependency>`
  }).join('\n')
  const resources = ['src', 'src-gen', 'resources', 'system', 'services'].filter(has).map((d) => {
    if (d === 'services') return `      <resource><directory>services</directory><targetPath>META-INF/services</targetPath></resource>`
    if (d === 'resources') return `      <resource><directory>resources</directory><targetPath>resources</targetPath></resource>`
    if (d === 'system') return `      <resource><directory>system</directory><targetPath>system</targetPath></resource>`
    return `      <resource><directory>${d}</directory><excludes><exclude>**/*.java</exclude><exclude>**/*.xtend</exclude><exclude>**/*.ecore</exclude><exclude>**/*.genmodel</exclude><exclude>**/*.mwe2</exclude><exclude>**/*.xtext</exclude><exclude>**/*.g</exclude><exclude>**/*._trace</exclude></excludes></resource>`
  }).join('\n')
  const pom = `<?xml version="1.0" encoding="UTF-8"?>
<!-- Generated by scripts/generate-poms.mjs; edit the table there, not this file. -->
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <parent><groupId>${GROUP}</groupId><artifactId>parent</artifactId><version>0.1.0-SNAPSHOT</version><relativePath>../../pom.xml</relativePath></parent>
  <artifactId>${name}</artifactId>
  <dependencies>
${dependencies}
  </dependencies>
  <build>
    <sourceDirectory>src</sourceDirectory>
    <resources>
${resources}
    </resources>
    <plugins>
      <plugin><groupId>org.codehaus.mojo</groupId><artifactId>build-helper-maven-plugin</artifactId></plugin>
      <plugin><groupId>org.eclipse.xtend</groupId><artifactId>xtend-maven-plugin</artifactId></plugin>
    </plugins>
  </build>
</project>
`
  fs.writeFileSync(path.join(dir, 'pom.xml'), pom)
}
console.log(`wrote ${Object.keys(MODULES).length} poms`)
