### Browser Sync Bug Fix
- [ ] Add periodic cleanup task to kill browsers of synced screens (except origin) in ClientProxy
- [ ] Ensure all browsers are killed when quitting the game in ClientProxy or SharedProxy
- [ ] Update LinkedScreenGroup to track synced browsers for cleanup
- [ ] Test the fix by syncing screens and checking browser count
