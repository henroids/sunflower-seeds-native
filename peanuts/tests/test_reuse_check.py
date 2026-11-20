"""The full check of a build's kept shaders on the Android side (issue #120, peanuts/docs/INCREMENTAL-BUILD.md "The full check, later"):
android/app/src/ondevice/java/dev/nxnative/android/PackService.java checkKept and android/app/src/main/java/dev/peanuts/build/ReuseCheck.java. They need
Android to run, so these tests read how they are wired, each for a fault a review found. No game files."""
import re
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
PACK = REPO / 'android/app/src/ondevice/java/dev/nxnative/android/PackService.java'
CHECK = REPO / 'android/app/src/main/java/dev/peanuts/build/ReuseCheck.java'
PREPARE = REPO / 'android/app/src/main/java/dev/peanuts/build/Prepare.java'


def check_kept():
    """PackService.checkKept's body"""
    s = PACK.read_text(encoding='utf-8')
    i = s.index('    private void checkKept(Job j) {')
    return s[i:s.index('\n    }\n', i)]


class Stop(unittest.TestCase):
    def test_a_game_or_a_waiting_job_stops_the_check_whenever_it_comes(self):
        # dm_pack_interrupt does nothing before the tool's run has begun (no run_tid), so one interrupt sent then was lost and the check translated on
        # beside the game (or a build waited for the whole check): the watcher sends it again every 3 s while the tool runs, and a game that starts asks
        # requestStop once, whose 20 s end of the process stands behind it
        body = check_kept()
        watch = body[body.index('Thread watch = new Thread('):body.index('watch.setDaemon(true);')]
        self.assertNotIn('return;', watch)
        self.assertIn('requestStop("game")', watch)
        self.assertRegex(watch, r'afterCheck != null')
        self.assertRegex(watch, r'engine\.interrupt\(\)')


class Pause(unittest.TestCase):
    def test_a_pause_while_a_job_waits_for_the_check_pauses_that_job(self):
        # the card's Pause of a build that waited for the check to stop (afterCheck) stopped the check instead, and the build started once the check had
        # stopped: the player's pause did nothing
        s = PACK.read_text(encoding='utf-8')
        p = s[s.index('        if (ACTION_PAUSE.equals(action)) {'):]
        p = p[:p.index('requestStop("user")')]
        self.assertRegex(p, r'Intent waiting = afterCheck;\s*if \(waiting != null\) \{\s*afterCheck = null;')
        self.assertRegex(p, r'w\.state\("paused", "user", null\);\s*try \{ w\.save\(\); \}')   # its file at once: a build that waits for it stops
        self.assertRegex(p, r'return START_STICKY;\s*\}\s*// PEANUTS end')
        # not save(w, true) while the check's worker runs: publish then posted note(w) with a worker alive, a paused job's "Waiting: ..." (the policy's pause),
        # ongoing, with Pause and no Resume, which the check's end (it removes only its own) left for good; and it told the panel the job was alive
        self.assertNotIn('save(w, true)', p)
        self.assertIn('pausedWaiting = w;', p)
        end = check_kept()
        end = end[end.index('main.post(() -> {'):]
        self.assertRegex(end, r'worker = null;\s*Job pw = pausedWaiting;\s*pausedWaiting = null;\s*if \(pw != null\) \{ publish\(pw, true\); nm\.notify\(NOTE, note\(pw\)\); \}')
        self.assertLess(end.index('publish(pw, true)'), end.index('if (next != null)'))   # before a job that came since takes NOTE


class Due(unittest.TestCase):
    """A due check the packer could never run was started again every two minutes for good, and held the checks of every other game (ReuseCheck.startAny
    takes one game at a time), and its job's split stayed"""
    def test_a_job_that_is_not_done_starts_no_check(self):
        s = CHECK.read_text(encoding='utf-8')
        start = s[s.index('    public static String start(Context c, File jobFile, String titleId) {'):]
        start = start[:start.index('\n    }\n')]
        self.assertIn('jobState(jobFile)', start)
        self.assertLess(start.index('jobState(jobFile)'), start.index('startForegroundService'))

    def test_the_packer_drops_a_check_an_app_update_made_impossible(self):
        s = PACK.read_text(encoding='utf-8')
        self.assertRegex(s, r'checkAsked && !check && j != null && "done"\.equals\(j\.state\(\)\) && j\.apkChanged\(\)[^\n]*\n[^\n]*CheckDue\.drop\(')

    def test_a_check_that_cannot_be_made_or_fails_is_counted_at_its_end(self):
        body = check_kept()
        end = body[body.index('} finally {'):]
        self.assertIn('dev.peanuts.build.CheckDue.ended(', end)
        self.assertLess(end.index('dev.peanuts.build.CheckDue.ended('), end.index('ReuseCheck.due(nb, j.titleId())'))   # before the split goes or stays

    def test_a_check_whose_process_ended_counts_at_the_next_start(self):
        # the finally block of checkKept does not run when the process ends (a low-memory kill, the 20 s end of a stop): such a check was asked again every
        # two minutes for good. Its mark (CheckDue.begin, before the tool) is counted at the next start (CheckDue.died)
        body = check_kept()
        self.assertIn('dev.peanuts.build.CheckDue.died(', body)
        self.assertLess(body.index('dev.peanuts.build.CheckDue.died('), body.index('dev.peanuts.build.CheckDue.begin('))
        self.assertLess(body.index('dev.peanuts.build.CheckDue.begin('), body.index('step(j, Job.SHADERS)'))

    def test_a_library_that_differed_is_the_checks_end_not_a_failure(self):
        s = PACK.read_text(encoding='utf-8')
        self.assertRegex(s, r'if \(checking && code == 3\)')   # shader_reuse.py full_check's DIFFER: it replaced the library itself

    def test_a_modded_build_does_not_take_the_check(self):
        # a build with code mods (Prepare.runModded) runs no shaders step and has no split: as the check's job it left the check unable to run
        s = PACK.read_text(encoding='utf-8')
        self.assertRegex(s, r'else if \(j\.modArgs\(\)\.isEmpty\(\)\) dev\.peanuts\.build\.ReuseCheck\.remember\(')

    def test_check_job_is_written_whole(self):
        s = CHECK.read_text(encoding='utf-8')
        remember = s[s.index('    public static void remember('):]
        remember = remember[:remember.index('\n    }\n')]
        self.assertIn('CheckDue.write(', remember)
        self.assertNotIn('Files.write(', remember)

    def test_the_notification_reads_whether_the_library_was_replaced(self):
        s = CHECK.read_text(encoding='utf-8')
        self.assertIn('return CheckDue.replacedText(reuseDir(noBackup, titleId), name, since);', s)


class Starts(unittest.TestCase):
    """onStartCommand with a check's start (ReuseCheck.EXTRA_CHECK)"""
    def on_start(self):
        s = PACK.read_text(encoding='utf-8')
        return s[s.index('final boolean checkAsked'):s.index('boolean go = ')]

    def nothing(self):
        """The branch that answers a check's start that runs nothing"""
        on = self.on_start()
        br = on[on.index('if (checkAsked && !check && (worker == null || !worker.isAlive())) {'):]
        br = br[:br.index('return START_NOT_STICKY;')]
        return '\n'.join(l.split('//')[0] for l in br.splitlines())   # the code, not what its comments tell of the code before

    def test_a_checks_start_that_runs_nothing_marks_no_job(self):
        # such a start went on to upstream's answer, startForeground(j): refused (a start from the background, Android's dataSync limit), it marks a job that
        # is not done paused "refused", and a build whose job.json was that job just then (its own start right behind) stopped with "Preparing paused"
        br = self.nothing()
        self.assertNotIn('startForeground(j)', br)
        self.assertRegex(br, r'try \{ startForeground\(id, id == NOTE \? note\(job\) : note\(\(Job\) null\), ServiceInfo\.FOREGROUND_SERVICE_TYPE_DATA_SYNC\); \}')

    def test_a_checks_start_that_runs_nothing_takes_no_builds_notification(self):
        # answered under NOTE ("Preparing a game", then removed), it took a failed or finished build's notification away, as the check did before it had its own
        br = self.nothing()
        self.assertRegex(br, r'int id = ending && !checkRunning \? NOTE : NOTE_CHECK;')

    def test_a_checks_start_while_a_workers_end_is_to_post_leaves_the_service_to_that_post(self):
        # in that window (9699ef8 sends such starts here) it removed the foreground and asked the service to stop: the post's start of a job that waited for the
        # check ("while this service is still in the foreground") then came from the background, and a stop while a foreground start is unanswered ends the process
        br = self.nothing()
        self.assertRegex(br, r'boolean ending = worker != null;')
        self.assertRegex(br, r'if \(!ending\) \{ stopForeground\(STOP_FOREGROUND_REMOVE\); stopSelf\(startId\); \}')

    def test_a_check_starts_only_once_the_last_workers_end_has_run(self):
        # a check's start between the last worker's end (its thread gone) and that end's post (worker = null, checkJob = null, stopSelf) started a second
        # worker the post then forgot: save and publish took the check's done job again, a job's start ran beside it, the service stopped under it
        on = self.on_start()
        check = on[on.index('final boolean check = checkAsked'):]
        check = check[:check.index(';')]
        self.assertIn('worker == null', check)
        self.assertNotIn('isAlive', check)


class Stale(unittest.TestCase):
    """What the packer sends to a client that binds after the check, and what a build takes from it"""
    def test_the_checks_end_leaves_no_job_to_send(self):
        # `job` stayed the check's done job after the check: a client that registered later was sent it (MSG_REGISTER publishes `job`), and the panel and a
        # build waiting for its own job took it for theirs
        body = check_kept()
        end = body[body.index('main.post(() -> {'):]
        self.assertRegex(end, r'if \(job == checkJob\) job = null;[^\n]*\n\s*checkJob = null;')

    def test_a_refused_check_start_leaves_no_job_to_send(self):
        # `job = j` comes before startForeground; refused, the check's done job stayed the service's job, and a client that binds is sent it (as after a check)
        s = PACK.read_text(encoding='utf-8')
        refused = s[s.index('        if (!startForeground(j)) {'):]
        refused = refused[:refused.index('return START_NOT_STICKY;')]
        self.assertRegex(refused, r'if \(check\) job = null;')

    def test_a_build_takes_only_its_own_jobs_messages(self):
        # Prepare took the newest job the packer sent, whatever its file: another job's "done" (the check's, one finished before) ended a build that had only
        # started ("Pack finished", its game added with a pack still being made)
        s = PREPARE.read_text(encoding='utf-8')
        on = s[s.index('        boolean onMessage(Message m) {'):]
        on = on[:on.index('\n        }\n')]
        self.assertRegex(on, r'String own = ownJob;\s*if \(own == null \|\| !own\.equals\(d\.getString\("file"\)\)\) return true;')
        self.assertLess(on.index('!own.equals('), on.index('fromService = j'))
        self.assertEqual(len(re.findall(r'ownJob = file\.getPath\(\);[^\n]*\n\s*bind\(\);', s)), 2)   # set before each bind: the build and the modded build


class Notice(unittest.TestCase):
    def test_a_timeout_during_the_check_leaves_no_notification(self):
        # onTimeout (Android's daily dataSync limit) detached the notification and posted note(j) again: during the check that is "Checking <game>'s shaders",
        # ongoing, which the check's end (STOP_FOREGROUND_REMOVE) no longer removes, so it stayed for good
        s = PACK.read_text(encoding='utf-8')
        t = s[s.index('    @Override public void onTimeout(int startId, int fgsType) {'):]
        t = t[:t.index('\n    }\n')]
        self.assertRegex(t, r'if \(j != null && j == checkJob\) \{[^\n]*\n[^\n]*requestStop\("timeout"\);\s*stopForeground\(STOP_FOREGROUND_REMOVE\);\s*stopSelf\(\);\s*return;')

    def test_the_check_has_a_notification_of_its_own(self):
        # it took the job notification's id (NOTE): a build that had failed or finished before lost its notification to "Checking ...", which the check's end
        # then removed (STOP_FOREGROUND_REMOVE)
        s = PACK.read_text(encoding='utf-8')
        self.assertRegex(s, r'private static final int NOTE_CHECK = NOTE \+ 8;')
        fg = s[s.index('    private boolean startForeground(Job j) {'):]
        fg = fg[:fg.index('\n    }\n')]
        # checkRunning too: checkKept's finally clears `checking` and then still works (CheckDue, the split's delete, seconds) before its post; a start then
        # took NOTE with the done job's "ready to play", and the post's REMOVE took that and a build's notification from before with it (found in review)
        self.assertIn('startForeground(checking || checkRunning ? NOTE_CHECK : NOTE, note(j), ', fg)
        self.assertIn('if (!checking && !checkRunning) nm.cancel(NOTE_CHECK);', fg)   # a job that waited takes the service over: no "Checking ..." stays

    def test_only_the_checks_job_is_shown_as_the_check(self):
        # note() said "Checking <job>'s shaders" (ongoing) for any job while the check ran: a job's own notification posted then (a job that waited and was
        # paused) would have read so, and stayed
        s = PACK.read_text(encoding='utf-8')
        note = s[s.index('    private Notification note(Job j) {'):]
        self.assertIn('if ((checking || checkRunning) && j == job) return b.setContentTitle("Checking "', note)


@unittest.skipUnless(shutil.which('javac') and shutil.which('java'), 'no JDK')
class Jvm(unittest.TestCase):
    def test_jvm(self):
        src = REPO / 'android/app/src/main/java/dev/peanuts/build/CheckDue.java'
        test = REPO / 'android/app/src/jvmtest/java/dev/peanuts/build/CheckDueJvmTest.java'
        with tempfile.TemporaryDirectory() as d:
            d = Path(d)
            r = subprocess.run(['javac', '-Xlint:all', '-Werror', '-encoding', 'UTF-8', '-d', str(d / 'classes'), str(src), str(test)], capture_output=True, text=True)
            self.assertEqual(r.returncode, 0, r.stdout + r.stderr)
            r = subprocess.run(['java', '-cp', str(d / 'classes'), 'dev.peanuts.build.CheckDueJvmTest'], capture_output=True, text=True)
            self.assertEqual(r.returncode, 0, r.stdout + r.stderr)
            self.assertIn('ok', r.stdout)


if __name__ == '__main__':
    unittest.main()
