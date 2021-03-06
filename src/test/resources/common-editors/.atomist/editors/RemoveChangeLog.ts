// Generated by Rug to TypeScript transpiler.

import { EditProject } from '@atomist/rug/operations/ProjectEditor'
import { Editor, Tags } from '@atomist/rug/operations/Decorators'
import { Project } from '@atomist/rug/model/Core'

/**
    RemoveChangeLog
    removes CHANGELOG file if present
 */
@Editor("RemoveChangeLog", "removes CHANGELOG.md file if present")
@Tags("documentation")
class RemoveChangeLog implements EditProject {

    edit(project: Project) {
        let changeLogFilename = "CHANGELOG.md"
        let p = project

        if (p.fileExists(changeLogFilename)) {
            p.deleteFile(changeLogFilename)
        }
    }
}
export let editor_removeChangeLog = new RemoveChangeLog();