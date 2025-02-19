# Copyright (C) 2021 IKUS Software inc. All rights reserved.
# IKUS Software inc. PROPRIETARY/CONFIDENTIAL.
# Use is subject to license terms.
import logging
import tkinter.messagebox
import webbrowser

import pkg_resources

import minarca_client
from minarca_client.core import Backup
from minarca_client.core.latest import LatestCheck, LatestCheckFailed
from minarca_client.locale import _
from minarca_client.ui import tkvue

logger = logging.getLogger(__name__)


class SettingsView(tkvue.Component):
    template = pkg_resources.resource_string('minarca_client.ui', 'templates/settings.html').decode("utf-8")

    def __init__(self, *args, **kwargs):
        self.backup = Backup()
        self.latest_check = LatestCheck()
        self.data = tkvue.Context(
            {
                'check_latest_version': self.backup.get_settings('check_latest_version'),
                'checking_for_update': False,  # True when background thread is running.
                'is_latest': None,
                'check_latest_version_error': None,
                'version': 'v' + minarca_client.__version__,
            }
        )
        super().__init__(*args, **kwargs)
        self.data.watch('check_latest_version', self.update_check_latest_version)
        self.root.bind('<<prompt_latest_version>>', self._prompt_latest_version)

        # Initialise stuff for latest version.
        if self.data['check_latest_version']:
            # After 5 secs, check for update.
            self.root.after(5000, self._check_latest_version)

    def update_check_latest_version(self, value):
        """
        Called to update the frequency.
        """
        self.backup.set_settings('check_latest_version', value)

    def _prompt_latest_version(self):
        self.data['checking_for_update'] = False
        latest_version = self.latest_check.get_latest_version()
        ret = tkinter.messagebox.askquestion(
            master=self.root,
            title=_("New version available"),
            message=_(
                "A new version of Minarca (%s) is available from IKUS Software. Do you want to upgrade your copy ?"
            )
            % latest_version,
            detail=_(
                "Minarca automatically checks for new updates. You can change how Minarca checks for updates in Minarca's settings."
            ),
        )
        if ret == 'no':
            # Operation cancel by user
            return
        # Open URL to Minarca Download page.
        url = self.latest_check.get_download_url()
        webbrowser.open(url)

    def _check_latest_version(self):
        self.get_event_loop().create_task(self._check_latest_version_task())

    async def _check_latest_version_task(self):
        self.data['checking_for_update'] = True
        self.data['is_latest'] = None
        self.data['check_latest_version_error'] = None

        # Query latest version.
        try:
            is_latest = await self.get_event_loop().run_in_executor(None, self.latest_check.is_latest)
            self.data['is_latest'] = is_latest
            if not is_latest:
                # Show dialog
                self._prompt_latest_version()
        except tkinter.TclError:
            # Swallow exception raised when application get destroyed.
            pass
        except LatestCheckFailed as e:
            self.data['check_latest_version_error'] = str(e)
        finally:
            self.data['checking_for_update'] = False

    def unlink(self):
        """
        Called to un register this agent from minarca server.
        """
        return_code = tkinter.messagebox.askyesno(
            parent=self.root,
            title=_('Are you sure ?'),
            message=_('Are you sure you want to disconnect this Minarca agent ?'),
            detail=_(
                'If you disconnect this computer, this Minarca agent will erase its identity and will no longer run backup on schedule.'
            ),
        )
        if not return_code:
            # Operation cancel by user.
            return
        self.backup.unlink()
        self.root.winfo_toplevel().destroy()
